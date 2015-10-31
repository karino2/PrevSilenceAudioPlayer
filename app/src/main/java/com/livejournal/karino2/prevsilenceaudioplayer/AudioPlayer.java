package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.Context;

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

    public void finalizePlayer() {
        playingState.finalizeState();
    }


    public void requestPrevSec() {
        pushCommand(Command.CommandType.PREVIOUS_SEC);
    }

    public void requestNextSec() {
        pushCommand(Command.CommandType.NEXT_SEC);
    }

    public interface StateChangedListener {
        void requestRestart();
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


    boolean isInsidePlayLoop = false;
    enum Status {
        STOP,
        PLAY,
        PAUSE,
    }


    PlayingState playingState;
    StateChangedListener listener;
    Status status;

    public AudioPlayer(StateChangedListener listener, long silenceThreshold, long silenceDurationThreshold) {
        this.listener = listener;
        playingState = new PlayingState(silenceThreshold, silenceDurationThreshold);
        status = Status.STOP;
    }

    public void requestPlayAudio(String audioFilePath) throws IOException {
        if(isInsidePlayLoop) {
            pushCommand(Command.createNewFileCommand(audioFilePath));
        }else {
            setAudioPath(audioFilePath);
            play();
        }
    }

    public void setAudioPath(String audioFilePath) throws IOException {
        playingState.setAudioPath(audioFilePath);
        playingState.prepare();
        status = Status.STOP;
    }

    public boolean isPlaying() {
        return status == Status.PLAY;
    }

    public boolean isInsidePlayLoop() {
        return isInsidePlayLoop;
    }
    public boolean atHead() { return playingState.atHead(); }

    public void requestStop() {
        if(isInsidePlayLoop) {
            pushCommand(Command.CommandType.STOP);
        }
    }

    long pendingSeekToUS = -1;

    public void play() throws IOException {
        isInsidePlayLoop = true;
        status = Status.PLAY;

        try {
            playingState.ensurePrepare();

            if(pendingSeekToUS != -1)
            {
                // Log.d("PrevSilence", "Prev: seekTo: " + pendingSeekToUS);
                playingState.seekTo(pendingSeekToUS);
                pendingSeekToUS = -1;
            }


            while (!isPendingCommandExists() && !playingState.isEnd()) {
                playingState.playOne();
            }


            // analyzer.debugPrint();

            if(isPendingCommandExists()) {
                handlePendingCommand();
            } else {
                // reach end

                playingState.finishPlaying();
                status = Status.STOP; // may be next is exist. so goto state STOP first.
                listener.reachEnd();
            }
        }finally {
            isInsidePlayLoop = false;
        }

    }


    private void handlePendingCommand() throws IOException {
        Command command = popCommand();
        switch(command.getCommandType()) {
            case STOP:
                playingState.finishPlaying();
                status = Status.STOP;
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
                zapWaitEvent();
                listener.requestMediaButtonWait();
                return;
            case PAUSE:
                status = Status.PAUSE;
                return;
            case NEXT:
                handleToNextOutsideLoop();
                // listener.requestNext();
                return;
            case PREVIOUS_SEC:
                handleToPreviousSecOutsideLoop();
                return;
            case NEXT_SEC:
                handleToNextSecOutsideLoop();
                return;
        }

    }

    private void zapWaitEvent() {
        ArrayList<Command> deleteCandidate = new ArrayList<>();
        for(Command command : commandQueue) {
            if(command.getCommandType() == Command.CommandType.MEDIABUTTON_WAIT) {
                deleteCandidate.add(command);
            }
        }
        commandQueue.removeAll(deleteCandidate);
    }

    private void handleToPreviousOutsideLoop() {
        pendingSeekToUS = playingState.getPreviousSilentEnd();
        // pendingSeekToUS
        // Log.d("PrevSilence", "Prev: get pendingSeekToUS: " + pendingSeekToUS + ", " + playingState.getCurrent());
        listener.requestRestart();
    }

    private void handleToNextOutsideLoop() {
        pendingSeekToUS = playingState.getNextSilentEnd();
        // Log.d("PrevSilence", "Next: get pendingSeekToUS: " + pendingSeekToUS + ", " + playingState.getCurrent());
        listener.requestRestart();
    }

    final long SMALL_JUMP_SEC = 3000;

    private void handleToPreviousSecOutsideLoop() {
        pendingSeekToUS = playingState.getDeltaFromCurrentUS(- SMALL_JUMP_SEC*1000);
        listener.requestRestart();
    }

    private void handleToNextSecOutsideLoop() {
        pendingSeekToUS = playingState.getDeltaFromCurrentUS(SMALL_JUMP_SEC*1000);
        listener.requestRestart();
    }
}
