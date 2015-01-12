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
    private int channelNum;

    public void debugPrint() {
        Log.d("PrevSilence", "silent size: " + silentSectionList.size());
        for(SilentSection ss : silentSectionList) {
            Log.d("PrevSilence", "ss: " + ss.begin + ", " + ss.duration + ", " + ss.isEnded);
        }
    }

    long lastAnalyzed;

    public void setChannelNum(int channelNum) {
        this.channelNum = channelNum;
    }

    public int getChannelNum() {
        return channelNum;
    }

    class SilentSection {
        public long begin;
        public long duration;
        public boolean isEnded;

        SilentSection(long begin, long duration, boolean isEnded){
            this.begin = begin;
            this.duration = duration;
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
    }

    public long getPreviousSilentEnd() {
        return getPreviousSilentEnd(current);
    }

    final long MARGIN_COUNT = 2000;

    // return samplecount. caller must convert to us.
    public long getPreviousSilentEnd(long from) {
        if(silentSectionList.size() == 0)
            return 0;
        SilentSection prev = silentSectionList.get(0);
        if(prev.getEnd() +MARGIN_COUNT > from)
            return 0;
        for(SilentSection cur : silentSectionList) {
            if(cur.getEnd() +MARGIN_COUNT > from) {
                return Math.max(0, prev.getEnd() - MARGIN_COUNT);
            }
            prev = cur;
        }
        return Math.max(0, prev.getEnd() - MARGIN_COUNT);
    }



    // ex. 44100
    int sampleRate;


    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
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
        // updateLastAnalyzed();
        long newCount = UsToSampleCount(beginUS);
        if(newCount > lastAnalyzed) {
            Log.d("PrevSilence", "last, newCount: " + lastAnalyzed + ", " + newCount);
            throw new UnsupportedOperationException("seek forward is not yet supported");
        }
        current = newCount;
    }

    private void updateLastAnalyzed() {
        lastAnalyzed = Math.max(lastAnalyzed, current);
    }

    final long SILENCE_THRESHOLD = 100;
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

        if(current +sampleLen <= lastAnalyzed) {
            current += sampleLen;
            return;
        }

        // current < lastAnalyzed < current+sampleLen
        if(current < lastAnalyzed) {
            begin = (int)(lastAnalyzed - current); // this must be smaller than sampleLen
            current = lastAnalyzed;
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
            silenceBegin = last.begin;
            duration = last.duration;
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
            updateLastAnalyzed();
        }
        if(insideSilence) {
            silentSectionList.add(new SilentSection(silenceBegin, duration, false));
        }
    }

    private boolean isSilence(short aShort) {
        return aShort <= SILENCE_THRESHOLD  && aShort >= -SILENCE_THRESHOLD;
    }

}
