package com.livejournal.karino2.prevsilenceaudioplayer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by karino on 1/12/15.
 */
public class AudioPlayer {

    enum Command {
        CANCEL,
        NEW_FILE
    }

    boolean isRunning = false;


    MediaExtractor extractor;
    AudioTrack audioTrack;
    MediaCodec codec;
    SilenceAnalyzer analyzer;

    public AudioPlayer() {
        extractor = new MediaExtractor();
        analyzer = new SilenceAnalyzer();
    }

    String pendingNewFile;


    public void playAudio(String audioFilePath) throws IOException {
        if(isRunning) {
            pendingCommandExists = true;
            pendingCommand = Command.NEW_FILE;
            pendingNewFile = audioFilePath;
        }else {
            setAudioPath(audioFilePath);
            play();
        }
    }

    public void setAudioPath(String audioFilePath) throws IOException {
        extractor.setDataSource(audioFilePath);
    }

    private void setupCodecAndOutputAudioTrack() throws IOException {
        MediaFormat format = extractor.getTrackFormat(0);

        // ex. 44100
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        String mime = format.getString(MediaFormat.KEY_MIME);

        int channelConfiguration = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;

        int minSize = AudioTrack.getMinBufferSize(sampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfiguration,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);


        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
    }




    Command pendingCommand;
    boolean pendingCommandExists = false;


    public void requestStop() {
        if(isRunning) {
            pendingCommandExists = true;
            pendingCommand = Command.CANCEL;
        }
    }

    public void play() throws IOException {
        isRunning = true;
        pendingCommandExists = false;

        try {
            setupCodecAndOutputAudioTrack();


            codec.start();

            audioTrack.play();
            extractor.selectTrack(0);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            final long TIMEOUT = 1000; // us.

            ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
            ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

            boolean outputDone = false;
            boolean inputEnd = false;

            int bufferingCount = 0;
            final int BUFFER_LIMIT = 10;

            analyzer.clear();
            analyzer.setDecodeBegin(0);

            while (!pendingCommandExists && !outputDone && bufferingCount < BUFFER_LIMIT) {
                bufferingCount++;

                if (!inputEnd) {
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


                int res = codec.dequeueOutputBuffer(info, TIMEOUT);
                if (res >= 0) {
                    bufferingCount = 0;

                    int outBufIndex = res;
                    ByteBuffer outBuf = codecOutputBuffers[outBufIndex];
                    analyzer.analyze(outBuf, info.size, 2); // 8bit, 1.  16bit 2.
                    byte[] chunk = new byte[info.size];
                    outBuf.get(chunk);
                    outBuf.clear();
                    if (chunk.length > 0) {
                        audioTrack.write(chunk, 0, chunk.length);
                    }

                    codec.releaseOutputBuffer(outBufIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }

                } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    codecOutputBuffers = codec.getOutputBuffers();
                } else {
                    Log.d("PrevSilence", "ignoring dequeue buffer return: " + res);
                }
            }

            finalizeCodecAndOutputAudioTrack();

            analyzer.debugPrint();

            if(pendingCommandExists) {
                handlePendingCommand();
            }
        }finally {
            isRunning = false;
        }

    }

    private void handlePendingCommand() throws IOException {
        pendingCommandExists = false;
        if(pendingCommand == Command.CANCEL) {
            return;
        }
        if(pendingCommand == Command.NEW_FILE) {
            setAudioPath(pendingNewFile);
            // TODO: need thread post here.
            // Thread.currentThread().
            return ;
        }
    }


    private void finalizeCodecAndOutputAudioTrack() {
        if(codec != null) {
            codec.stop();
            codec.release();
            codec = null;
        }
        if(audioTrack != null) {
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
    }

}
