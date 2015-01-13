package com.livejournal.karino2.prevsilenceaudioplayer;

import android.util.Log;

import java.io.IOException;

/**
 * Created by karino on 1/12/15.
 */
public class AudioPlayer {
    public interface RestartListener {
        void requestRestart();
    }


    public void requestPrev(boolean withDelay) {
        if(!isRunning)
        {
            Log.d("PrevSilence", "Prev: not running! ignore for a while.");
            // listener.requestRestart();
            return;
        }
        pendingCommandExists = true;
        pendingCommand = withDelay? Command.PREVIOUS_WITHDELAY : Command.PREVIOUS;
    }

    enum Command {
        STOP,
        PREVIOUS,
        PREVIOUS_WITHDELAY,
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
        playingState.prepare();
    }







    Command pendingCommand;
    boolean pendingCommandExists = false;

    public boolean isRunning() {
        return isRunning;
    }

    public void requestStop() {
        if(isRunning) {
            pendingCommandExists = true;
            pendingCommand = Command.STOP;
        }
    }

    long pendingSeekTo = -1;

    public void play() throws IOException {
        isRunning = true;
        pendingCommandExists = false;

        try {

            if(pendingSeekTo != -1)
            {
                Log.d("PrevSilence", "Prev: seekTo: " + pendingSeekTo);
                playingState.seekTo(pendingSeekTo);
                pendingSeekTo = -1;
            }


            while (!pendingCommandExists && !playingState.isEnd()) {
                playingState.playOne();
            }


            // analyzer.debugPrint();

            if(pendingCommandExists) {
                handlePendingCommand();
            } else {
                playingState.finishPlaying();
            }
        }finally {
            isRunning = false;
        }

    }


    private void handlePendingCommand() throws IOException {
        pendingCommandExists = false;
        if(pendingCommand == Command.STOP) {
            playingState.finishPlaying();
            return;
        }
        if(pendingCommand == Command.NEW_FILE) {
            playingState.finishPlaying();
            setAudioPath(pendingNewFile);
            listener.requestRestart();
            return ;
        }
        if(pendingCommand == Command.PREVIOUS) {
            pendingSeekTo = playingState.getPreviousSilentEnd();
            Log.d("PrevSilence", "Prev: get pendingSeekTo: " + pendingSeekTo + ", " + playingState.getCurrent());
            // Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
            listener.requestRestart();
            return;
        }
        if(pendingCommand == Command.PREVIOUS_WITHDELAY) {
            long rawSeekTo = playingState.getPreviousSilentEnd();
            pendingSeekTo = Math.max(rawSeekTo-500000, 0); // bluetooth become silent for a while when operate button. wait some time for this reason.
            Log.d("PrevSilence", "Prev: get pendingSeekTo withDlay: " + pendingSeekTo + ", " + playingState.getCurrent());
            // Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
            listener.requestRestart();
            return;
        }

    }




}
