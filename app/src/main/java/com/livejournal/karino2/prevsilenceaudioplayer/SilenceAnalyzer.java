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
    public void debugPrint() {
        Log.d("PrevSilence", "silent size: " + silentSectionList.size());
        for(SilentSection ss : silentSectionList) {
            Log.d("PrevSilence", "ss: " + ss.silenceBeginSampleCount + ", " + ss.silenceDurationSampleCount + ", " + ss.isEnded);
        }
    }

    /*
    long lastAnalyzedFrameUS;
    // ex. 44100
    int sampleRate;
    */

    class SilentSection {
        public long decodeBeginUS;
        public long silenceBeginSampleCount; // dist from decodeBegin.
        public long silenceDurationSampleCount;
        public boolean isEnded;

        SilentSection(long decodeUS, long begin, long duration, boolean isEnded){
            decodeBeginUS = decodeUS;
            silenceBeginSampleCount = begin;
            silenceDurationSampleCount = duration;
            this.isEnded = isEnded;
        }
    }

    public boolean isLastSectionContinue() {
        if(silentSectionList.size() == 0)
            return false;
        return !silentSectionList.get(silentSectionList.size()-1).isEnded;
    }

    public void clear() {
        silentSectionList.clear();
        sampleCount = 0;
        decodeBeginUS = 0;
    }

    long sampleCount = 0;
    long decodeBeginUS;
    public void setDecodeBegin(long beginUS) {
        decodeBeginUS = beginUS;
        sampleCount = 0;
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

        // Assume oneSampleByteNum always 2 for a while, i.e. 16BIT PCM.
        short[] shorts = new short[chunkLen/2];
        chunk.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);


        boolean insideSilence = false;
        long silenceBegin = 0;
        long duration = 0;
        if(isLastSectionContinue())
        {
            SilentSection last = popFromList();
            insideSilence = true;
            silenceBegin = last.silenceBeginSampleCount;
            duration = last.silenceDurationSampleCount;
        }

        for(int i = 0; i < shorts.length; i++)
        {

            if(insideSilence)
            {
                if(isSilence(shorts[i])){
                    duration++;
                } else {
                    if(duration > MINIMUM_DURATION) {
                        silentSectionList.add(new SilentSection(decodeBeginUS, silenceBegin, duration, true));
                        Log.d("PrevSilence", "endSilence: " + sampleCount + ", " + shorts[i]);
                    }
                    silenceBegin = 0;
                    duration = 0;
                    insideSilence = false;
                }
            } else if(isSilence(shorts[i]))
            {
                insideSilence = true;
                silenceBegin = sampleCount;
            }

            sampleCount++;
        }
        if(insideSilence) {
            silentSectionList.add(new SilentSection(decodeBeginUS, silenceBegin, duration, false));
        }
    }

    private boolean isSilence(short aShort) {
        return aShort <= SILENCE_THRESHOLD  && aShort >= -SILENCE_THRESHOLD;
    }

}
