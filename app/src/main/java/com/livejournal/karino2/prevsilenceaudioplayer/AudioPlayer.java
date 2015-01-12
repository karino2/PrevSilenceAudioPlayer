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
    public interface RestartListener {
        void requestRestart();
    }


    public void requestPrev() {
        if(!isRunning)
        {
            listener.requestRestart();
            return;
        }
        pendingCommandExists = true;
        pendingCommand = Command.PREVIOUS;
    }

    enum Command {
        CANCEL,
        PREVIOUS,
        NEW_FILE
    }

    boolean isRunning = false;


    MediaExtractor extractor;
    AudioTrack audioTrack;
    MediaCodec codec;
    SilenceAnalyzer analyzer;
    RestartListener listener;

    public AudioPlayer(RestartListener listener) {
        extractor = new MediaExtractor();
        analyzer = new SilenceAnalyzer();
        this.listener = listener;
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
        analyzer.setSampleRate(sampleRate);
        analyzer.setChannelNum(channelConfiguration == AudioFormat.CHANNEL_OUT_MONO ? 1 : 2);
    }




    Command pendingCommand;
    boolean pendingCommandExists = false;


    public void requestStop() {
        if(isRunning) {
            pendingCommandExists = true;
            pendingCommand = Command.CANCEL;
        }
    }

    long pendingSeekTo = -1;

    public void play() throws IOException {
        isRunning = true;
        pendingCommandExists = false;

        try {
            setupCodecAndOutputAudioTrack();

            PlayingState playing = new PlayingState(extractor, codec, audioTrack, analyzer);
            playing.prepare();

            if(pendingSeekTo != -1)
            {
                playing.seekTo(pendingSeekTo);
                pendingSeekTo = -1;
            }


            while (!pendingCommandExists && !playing.isEnd()) {
                playing.playOne();
            }

            finalizeCodecAndOutputAudioTrack();

            // analyzer.debugPrint();

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
            listener.requestRestart();
            return ;
        }
        if(pendingCommand == Command.PREVIOUS) {
            pendingSeekTo = analyzer.getPreviousSilentEnd();
            Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
            listener.requestRestart();
            return;
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
