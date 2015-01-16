package com.livejournal.karino2.prevsilenceaudioplayer;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by karino on 1/12/15.
 */
public class SilenceAnalyzer {
    private int channelNum = 1;

    public void debugPrint() {
        Log.d("PrevSilence", "silent size: " + silentSectionList.size());
        for(SilentSection ss : silentSectionList) {
            Log.d("PrevSilence", "ss: " + ss.begin + ", " + ss.duration + ", " + ss.isEnded);
        }
    }

    long lastAnalyzedCount;

    /*
        this.sampleRate = sampleRate;
        recalculateLastAnalyzedCount();
        current = UsToSampleCount(currentUS);

     */
    public void setChannelNum(int channelNum) {
        long currentUS = sampleCountToUS(current);
        this.channelNum = channelNum;
        recalculateLastAnalyzedCount();
        current = UsToSampleCount(currentUS);
    }

    public int getChannelNum() {
        return channelNum;
    }

    class SilentSection {
        public long begin;
        public long duration;
        public boolean isEnded;

        SilentSection(long beginCount, long durationCount, boolean isEnded){
            this.begin =  sampleCountToUS(beginCount);
            this.duration = sampleCountToUS(durationCount);
            this.isEnded = isEnded;
        }

        public long getEnd() {
            return begin + duration;
        }
    }

    public boolean isLastSectionContinue() {
        if(silentSectionList.size() == 0)
            return false;
        return !silentSectionList.get(silentSectionList.size()-1).isEnded;
    }

    public void clear() {
        silentSectionList.clear();
        current = 0;
        lastAnalyzedCount = 0;
        lastAnalyzedUS = 0;
    }

    public long getPreviousSilentEnd() {
        return getPreviousSilentEnd(sampleCountToUS(current));
    }

    final long MARGIN_NS = 500000;

    public long getPreviousSilentEnd(long fromUS) {
        if(silentSectionList.size() == 0)
            return 0;
        SilentSection prev = silentSectionList.get(0);
        if(prev.getEnd() + MARGIN_NS > fromUS)
            return 0;
        for(SilentSection cur : silentSectionList) {
            if(cur.getEnd() + MARGIN_NS > fromUS) {
                return Math.max(0, prev.getEnd() - MARGIN_NS);
            }
            prev = cur;
        }
        return Math.max(0, prev.getEnd() - MARGIN_NS);
    }

    // best effort. If there are no available next, return lastAnalyzedUS.
    public long getNextSilentEnd() {
        if(silentSectionList.size() == 0)
            return Math.max(0, lastAnalyzedUS-MARGIN_NS);
        long currentUS = sampleCountToUS(current);
        for(SilentSection cur : silentSectionList) {
            if (cur.getEnd() > currentUS)
                return Math.max(0, cur.getEnd() - MARGIN_NS);
        }
        return Math.max(0, lastAnalyzedUS-MARGIN_NS);
    }



    int sampleRate = 44100;


    public void setSampleRate(int sampleRate) {
        long currentUS = sampleCountToUS(current);
        this.sampleRate = sampleRate;
        recalculateLastAnalyzedCount();
        current = UsToSampleCount(currentUS);
    }

    public final long sampleCountToUS(long count) {
        return count*1000000/(sampleRate*channelNum);
    }

    public final long UsToSampleCount(long us) {
        return us*sampleRate*channelNum/1000000;
    }

    long current = 0;

    public long getCurrent() {
        return current;
    }

    public void setDecodeBegin(long beginUS) {
        if(beginUS > lastAnalyzedUS) {
            Log.d("PrevSilence", "last, newUS: " + lastAnalyzedUS + ", " + beginUS);
            throw new UnsupportedOperationException("seek forward is not yet supported");
        }
        long newCount = UsToSampleCount(beginUS);
        current = newCount;
    }

    void recalculateLastAnalyzedCount() {
        lastAnalyzedCount = UsToSampleCount(lastAnalyzedUS);
    }

    long lastAnalyzedUS;
    private void updateLastAnalyzed() {
        lastAnalyzedCount = Math.max(lastAnalyzedCount, current);
        lastAnalyzedUS = sampleCountToUS(lastAnalyzedCount);
    }

    // final long SILENCE_THRESHOLD = 100;
    final long SILENCE_THRESHOLD = 1000;
    final long MINIMUM_DURATION=10000;

    List<SilentSection> silentSectionList = new ArrayList<>();

    SilentSection popFromList() {
        SilentSection ret = silentSectionList.get(silentSectionList.size()-1);
        silentSectionList.remove(silentSectionList.size()-1);
        return ret;
    }

    public void analyze(ByteBuffer chunk, int chunkLen, int oneSampleByteNum)
    {
        if(oneSampleByteNum != 2)
            throw new UnsupportedOperationException("Only support 16bit PCM for a while");

        int begin = 0;
        // Assume oneSampleByteNum always 2 for a while, i.e. 16BIT PCM.
        int sampleLen = chunkLen/2;

        if(current +sampleLen <= lastAnalyzedCount) {
            current += sampleLen;
            return;
        }

        // current < lastAnalyzedCount < current+sampleLen
        if(current < lastAnalyzedCount) {
            begin = (int)(lastAnalyzedCount - current); // this must be smaller than sampleLen
            current = lastAnalyzedCount;
        }


        short[] shorts = new short[sampleLen];
        chunk.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);


        boolean insideSilence = false;
        long silenceBegin = 0;
        long duration = 0;
        if(isLastSectionContinue())
        {
            SilentSection last = popFromList();
            insideSilence = true;
            silenceBegin = UsToSampleCount(last.begin);
            duration = UsToSampleCount(last.duration);
        }

        for(int i = begin; i < shorts.length; i++)
        {

            if(insideSilence)
            {
                if(isSilence(shorts[i])){
                    duration++;
                } else {
                    if(duration > MINIMUM_DURATION) {
                        silentSectionList.add(new SilentSection(silenceBegin, duration, true));
                        // Log.d("PrevSilence", "endSilence: " + current + ", " + shorts[i]);
                    }
                    silenceBegin = 0;
                    duration = 0;
                    insideSilence = false;
                }
            } else if(isSilence(shorts[i]))
            {
                insideSilence = true;
                silenceBegin = current;
            }

            current++;
        }
        updateLastAnalyzed();

        if(insideSilence) {
            silentSectionList.add(new SilentSection(silenceBegin, duration, false));
        }
    }

    private boolean isSilence(short aShort) {
        return aShort <= SILENCE_THRESHOLD  && aShort >= -SILENCE_THRESHOLD;
    }

}
