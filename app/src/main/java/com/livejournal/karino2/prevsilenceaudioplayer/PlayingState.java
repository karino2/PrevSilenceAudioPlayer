package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by karino on 1/12/15.
 */
public class PlayingState {

    MediaCodec codec;
    AudioTrack audioTrack;
    MediaExtractor extractor;
    SilenceAnalyzer analyzer;
    private String lastAudioPath;
    Context context;

    public PlayingState(long silenceThreshold, long silenceDurationThreshold) {
        extractor = new MediaExtractor();
        analyzer = new SilenceAnalyzer(silenceThreshold, silenceDurationThreshold);
    }


    boolean audioPathSet = false;
    public void setAudioPath(String audioFilePath) throws IOException {
        if(isPlayReady()) {
            finishPlaying();
        }
        extractor = new MediaExtractor();
        extractor.setDataSource(context, Uri.parse(audioFilePath), null);
        audioPathSet = true;
    }

    public void gotoHead() {
        if(isPlayReady()) {
            finishPlaying();
        }
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
    }


    private void setupCodecAndOutputAudioTrack() throws IOException {
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        MediaFormat format = extractor.getTrackFormat(0);

        String mime = format.getString(MediaFormat.KEY_MIME);
        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        updateFormat(format);

        codec.start();
        audioTrack.play();
    }

    MediaFormat prevFormat = null;
    private void updateFormat(MediaFormat format) {
        // ex. 44100
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelConfiguration = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;

        int minSize = AudioTrack.getMinBufferSize(sampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
        ensureAudioTrack(sampleRate, channelConfiguration, minSize);


        analyzer.setSampleRate(sampleRate);
        analyzer.setChannelNum(channelConfiguration == AudioFormat.CHANNEL_OUT_MONO ? 1 : 2);

        prevFormat = format;
    }

    private void ensureAudioTrack(int sampleRate, int channelConfiguration, int minSize) {
        if(audioTrack != null) {
            if(audioTrack.getSampleRate() == sampleRate
                && audioTrack.getChannelConfiguration() == channelConfiguration
                ) {
                return;
            }
            audioTrack.release();
        }
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfiguration,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
    }


    MediaCodec.BufferInfo info;

    ByteBuffer[] codecInputBuffers;
    ByteBuffer[] codecOutputBuffers ;

    boolean outputDone = false;
    boolean inputEnd = false;
    int bufferingCount = 0;
    final int BUFFER_LIMIT = 10;
    final long TIMEOUT = 1000; // us.


    long currentPos = 0;

    public boolean isPlayReady() {
        return playReady;
    }

    public void ensurePrepare() throws IOException {
        if(!playReady)
            prepare();
    }

    boolean playReady = false;
    public void prepare() throws IOException {
        if(!audioPathSet) {
            setAudioPath(lastAudioPath);
        }

        setupCodecAndOutputAudioTrack();


        extractor.selectTrack(0);

        info = new MediaCodec.BufferInfo();

        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        outputDone = false;
        inputEnd = false;

        bufferingCount = 0;
        currentPos = 0;

        analyzer.clear();
        analyzer.setDecodeBegin(0);
        playReady = true;
    }

    public long getPreviousSilentEnd() {
        return analyzer.getPreviousSilentEnd();
    }

    public boolean atHead() {
        return !isPlayReady() || currentPos == 0;
    }

    public void finishPlaying() {
        if(codec != null) {
            codec.stop();
            codec.release();
            codec = null;
        }
        if(audioTrack != null) {
            audioTrack.flush();
        }
        playReady = false;
    }

    private void finalizeAudioTrack() {
        audioTrack.flush();
        audioTrack.release();
    }


    public boolean isEnd() {
        return outputDone || bufferingCount >= BUFFER_LIMIT;
    }

    private void outputPCM(MediaCodec.BufferInfo info, int outBufIndex, ByteBuffer outBuf) {
        int chunkFrom = 0;

        if(skipUntil != -1) {
            chunkFrom = (int)(skipUntil - currentPos)*2; // this must be small because is already seeked.
            // still skip.
            if(chunkFrom > info.size) {
                outBuf.clear();
                codec.releaseOutputBuffer(outBufIndex, false);
                currentPos += info.size/2;
                return;
            }
            skipUntil = -1;
        }


        byte[] chunk = new byte[info.size];
        outBuf.get(chunk);
        outBuf.clear();
        if (chunk.length > 0) {
            audioTrack.write(chunk, chunkFrom, chunk.length-chunkFrom);
        }

        codec.releaseOutputBuffer(outBufIndex, false);
        currentPos += info.size/2;

    }


    long skipUntil = -1;
    public void seekTo(long tillUS) {
        // long beforeST = extractor.getSampleTime();

        skipUntil = analyzer.UsToSampleCount(tillUS);
        extractor.seekTo(tillUS, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        long currentUS = extractor.getSampleTime();
        analyzer.setDecodeBegin(currentUS);
        currentPos = analyzer.UsToSampleCount(currentUS);

        // should never happen, but occur sometime (why seekto go after tillUS?)
        if(skipUntil < currentPos)
            skipUntil = -1;

        // Log.d("PrevSilence", "afterSeek: " + currentPos + ", curUS: " + currentUS + ", beforeUS " + beforeST + ", tillUS+" + tillUS);
    }

    private void pushToCodec() {
        int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT);
        if (inputBufIndex >= 0) {
            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
            long presentTimeUS = extractor.getSampleTime();

            int sampleSize = extractor.readSampleData(dstBuf, 0);
            if (sampleSize >= 0) {
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
            pushToCodec();
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
        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat oformat = codec.getOutputFormat();
            // Log.d("PrevSilence", "output format has changed to " + oformat);
            if(formatEquals(oformat, prevFormat)) {
                // Log.d("PrevSilence", "same format, do nothing.");
                return;
            }

            if(skipUntil != -1) {
                Log.d("PrevSilence", "output format has changed during skip, not supported. just ignore skip");
                skipUntil = -1;
            }
            if(audioTrack != null)
                audioTrack.flush();

            long currentUS = analyzer.sampleCountToUS(currentPos);
            updateFormat(oformat);
            currentPos = analyzer.UsToSampleCount(currentUS);
            audioTrack.play();
            info = new MediaCodec.BufferInfo();
        } else {
            Log.d("PrevSilence", "ignoring dequeue buffer return: " + res);
        }
    }

    private boolean formatEquals(MediaFormat format1, MediaFormat format2) {
        return (format1.getInteger(MediaFormat.KEY_SAMPLE_RATE) == format2.getInteger(MediaFormat.KEY_SAMPLE_RATE)) &&
                (format1.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == format2.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
    }

    public long getCurrent() {
        return analyzer.getCurrent();
    }

    public void setLastAudioPath(String lastAudioPath) {
        this.lastAudioPath = lastAudioPath;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public long getNextSilentEnd() {
        return analyzer.getNextSilentEnd();
    }

    public void finalizeState() {
        if(audioTrack != null)
            finalizeAudioTrack();
    }
}
