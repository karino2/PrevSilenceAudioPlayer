package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.Context;
import android.util.Log;

import java.io.IOException;

/**
 * Created by karino on 1/12/15.
 */
public class AudioPlayer {

    public void setContext(Context context) {
        playingState.setContext(context);
    }

    public void setLastAudioPath(String lastAudioPath) {
        playingState.setLastAudioPath(lastAudioPath);
    }

    public void requestPause() {
        pendingCommand = Command.PAUSE;
        pendingCommandExists = true;
    }

    public void requestNext() {
        pendingCommand = Command.NEXT;
        pendingCommandExists = true;
    }

    public interface StateChangedListener {
        void requestRestart();
        void requestNext();
        void reachEnd();
    }


    public void requestPrev(boolean withDelay) {
        if(!isRunning)
        {
            if(playingState.isPlayReady()) {
                handleToPreviousOutsideLoop();
                return;
            }
            Log.d("PrevSilence", "Prev: not playing! ignore for a while.");
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
        PAUSE,
        NEXT,
        NEW_FILE
    }

    boolean isRunning = false;


    PlayingState playingState;
    StateChangedListener listener;

    public AudioPlayer(StateChangedListener listener) {
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
            playingState.ensurePrepare();

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
                listener.reachEnd();
            }
        }finally {
            isRunning = false;
        }

    }


    private void handlePendingCommand() throws IOException {
        pendingCommandExists = false;
        switch(pendingCommand) {
            case STOP:
                playingState.finishPlaying();
                return;
            case NEW_FILE:
                playingState.finishPlaying();
                setAudioPath(pendingNewFile);
                listener.requestRestart();
                return ;
            case PREVIOUS:
                handleToPreviousOutsideLoop();
                return;
            case PREVIOUS_WITHDELAY:
                long rawSeekTo = playingState.getPreviousSilentEnd();
                pendingSeekTo = Math.max(rawSeekTo-500000, 0); // bluetooth become silent for a while when operate button. wait some time for this reason.
                Log.d("PrevSilence", "Prev: get pendingSeekTo withDlay: " + pendingSeekTo + ", " + playingState.getCurrent());
                // Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
                listener.requestRestart();
                return;
            case PAUSE:
                // do nothing.
                return;
            case NEXT:
                listener.requestNext();
                return;
        }

    }

    private void handleToPreviousOutsideLoop() {
        pendingSeekTo = playingState.getPreviousSilentEnd();
        Log.d("PrevSilence", "Prev: get pendingSeekTo: " + pendingSeekTo + ", " + playingState.getCurrent());
        // Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
        listener.requestRestart();
    }


}
