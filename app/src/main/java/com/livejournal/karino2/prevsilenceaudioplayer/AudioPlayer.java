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


    PlayingState playingState;
    RestartListener listener;

    public AudioPlayer(RestartListener listener) {
        this.listener = listener;
        playingState = new PlayingState();
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
        playingState.setAudioPath(audioFilePath);
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
            playingState.prepare();

            if(pendingSeekTo != -1)
            {
                playingState.seekTo(pendingSeekTo);
                pendingSeekTo = -1;
            }


            while (!pendingCommandExists && !playingState.isEnd()) {
                playingState.playOne();
            }

            playingState.finishPlaying();

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
            pendingSeekTo = playingState.getPreviousSilentEnd();
            // Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
            listener.requestRestart();
            return;
        }

    }




}
