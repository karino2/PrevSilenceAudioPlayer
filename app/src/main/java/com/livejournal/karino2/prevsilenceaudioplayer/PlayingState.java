package com.livejournal.karino2.prevsilenceaudioplayer;

import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by karino on 1/12/15.
 */
public class PlayingState {

    MediaCodec codec;
    AudioTrack audioTrack;
    MediaExtractor extractor;
    SilenceAnalyzer analyzer;

    public PlayingState(MediaExtractor extractor1, MediaCodec codec1, AudioTrack audioTrack1, SilenceAnalyzer analyzer1) {
        extractor = extractor1;
        codec = codec1;
        audioTrack = audioTrack1;
        analyzer = analyzer1;
    }



    MediaCodec.BufferInfo info;

    ByteBuffer[] codecInputBuffers;
    ByteBuffer[] codecOutputBuffers ;

    boolean outputDone = false;
    boolean inputEnd = false;
    int bufferingCount = 0;
    final int BUFFER_LIMIT = 10;
    final long TIMEOUT = 1000; // us.


    public void prepare() {
        codec.start();

        audioTrack.play();

        extractor.selectTrack(0);

        info = new MediaCodec.BufferInfo();

        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        outputDone = false;
        inputEnd = false;

        bufferingCount = 0;

        analyzer.clear();
        analyzer.setDecodeBegin(0);
    }

    public boolean isEnd() {
        return outputDone || bufferingCount >= BUFFER_LIMIT;
    }

    private void outputPCM(MediaCodec.BufferInfo info, int outBufIndex, ByteBuffer outBuf) {
        byte[] chunk = new byte[info.size];
        outBuf.get(chunk);
        outBuf.clear();
        if (chunk.length > 0) {
            audioTrack.write(chunk, 0, chunk.length);
        }

        codec.releaseOutputBuffer(outBufIndex, false);
    }

    private void pushToCodec(ByteBuffer[] codecInputBuffers) {
        int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT);
        if (inputBufIndex >= 0) {
            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
            long presentTimeUS = extractor.getSampleTime();

            int sampleSize = extractor.readSampleData(dstBuf, 0);
            if (sampleSize >= 0) {
                // check silent here.

                codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentTimeUS, 0);
                extractor.advance();
            } else {
                inputEnd = true;
                codec.queueInputBuffer(inputBufIndex, 0, 0, presentTimeUS, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        }
    }

    public void playOne() {
        bufferingCount++;

        if (!inputEnd) {
            pushToCodec(codecInputBuffers);
        }

        decodeAndOutput();
    }

    private void decodeAndOutput() {
        int res = codec.dequeueOutputBuffer(info, TIMEOUT);
        if (res >= 0) {
            bufferingCount = 0;

            int outBufIndex = res;
            ByteBuffer outBuf = codecOutputBuffers[outBufIndex];

            analyzer.analyze(outBuf, info.size, 2); // 8bit, 1.  16bit 2.
            // Log.d("PrevSilence", "size: " + info.size); about 2304.
            outputPCM(info, outBufIndex, outBuf);

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                outputDone = true;
            }

        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            codecOutputBuffers = codec.getOutputBuffers();
        } else {
            Log.d("PrevSilence", "ignoring dequeue buffer return: " + res);
        }
    }
}
