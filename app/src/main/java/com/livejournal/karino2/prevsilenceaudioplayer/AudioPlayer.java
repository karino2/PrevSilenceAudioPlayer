package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        pushCommand(Command.CommandType.PAUSE);
    }

    public void requestNext(boolean withDelay) {
        if(withDelay) {
            pushMediaButtonWaitCommand();
        }
        pushCommand(Command.CommandType.NEXT);
    }

    public void gotoHead() {
        playingState.gotoHead();
    }

    public interface StateChangedListener {
        void requestRestart();
        void requestNext();
        void requestMediaButtonWait();
        void reachEnd();
    }

    List<Command> commandQueue = new ArrayList<>();
    synchronized boolean isPendingCommandExists() {
        return commandQueue.size() != 0;
    }

    synchronized Command popCommand() {
        Command ret = commandQueue.get(0);
        commandQueue.remove(0);
        return ret;
    }

    synchronized void pushCommand(Command command) {
        commandQueue.add(command);
    }

    synchronized void pushCommand(Command.CommandType typ) {
        pushCommand(Command.createCommand(typ));
    }


    public void requestPrev(boolean withDelay) {
        if(withDelay) {
            pushMediaButtonWaitCommand();
        }
        pushCommand(Command.CommandType.PREVIOUS);
    }

    public void pushMediaButtonWaitCommand() {
        pushCommand(Command.CommandType.MEDIABUTTON_WAIT);
    }


    boolean isRunning = false;


    PlayingState playingState;
    StateChangedListener listener;

    public AudioPlayer(StateChangedListener listener) {
        this.listener = listener;
        playingState = new PlayingState();
    }

    public void playAudio(String audioFilePath) throws IOException {
        if(isRunning) {
            pushCommand(Command.createNewFileCommand(audioFilePath));
        }else {
            setAudioPath(audioFilePath);
            play();
        }
    }

    public void setAudioPath(String audioFilePath) throws IOException {
        playingState.setAudioPath(audioFilePath);
        playingState.prepare();
    }

    public boolean isRunning() {
        return isRunning;
    }
    public boolean atHead() { return playingState.atHead(); }

    public void requestStop() {
        if(isRunning) {
            pushCommand(Command.CommandType.STOP);
        }
    }

    long pendingSeekTo = -1;

    public void play() throws IOException {
        isRunning = true;

        try {
            playingState.ensurePrepare();

            if(pendingSeekTo != -1)
            {
                Log.d("PrevSilence", "Prev: seekTo: " + pendingSeekTo);
                playingState.seekTo(pendingSeekTo);
                pendingSeekTo = -1;
            }


            while (!isPendingCommandExists() && !playingState.isEnd()) {
                playingState.playOne();
            }


            // analyzer.debugPrint();

            if(isPendingCommandExists()) {
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
        Command command = popCommand();
        switch(command.getCommandType()) {
            case STOP:
                playingState.finishPlaying();
                return;
            case NEW_FILE:
                playingState.finishPlaying();
                setAudioPath(command.getFilePath());
                listener.requestRestart();
                return ;
            case PREVIOUS:
                handleToPreviousOutsideLoop();
                return;
            case MEDIABUTTON_WAIT:
                // TODO: zap later MEDIABUTTON_WAIT event here.
                listener.requestMediaButtonWait();
                return;
            /*
            case PREVIOUS_WITHDELAY:
                long rawSeekTo = playingState.getPreviousSilentEnd();
                pendingSeekTo = Math.max(rawSeekTo-500000, 0); // bluetooth become silent for a while when operate button. wait some time for this reason.
                Log.d("PrevSilence", "Prev: get pendingSeekTo withDlay: " + pendingSeekTo + ", " + playingState.getCurrent());
                // Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
                listener.requestRestart();
                return;
                */
            case PAUSE:
                // do nothing.
                return;
            case NEXT:
                handleToNextOutsideLoop();
                // listener.requestNext();
                return;
        }

    }

    private void handleToPreviousOutsideLoop() {
        pendingSeekTo = playingState.getPreviousSilentEnd();
        Log.d("PrevSilence", "Prev: get pendingSeekTo: " + pendingSeekTo + ", " + playingState.getCurrent());
        // Log.d("PrevSilence", "seekTo, current: " + pendingSeekTo + ", " + analyzer.getCurrent());
        listener.requestRestart();
    }

    private void handleToNextOutsideLoop() {
        pendingSeekTo = playingState.getNextSilentEnd();
        Log.d("PrevSilence", "Next: get pendingSeekTo: " + pendingSeekTo + ", " + playingState.getCurrent());
        listener.requestRestart();
    }

}
