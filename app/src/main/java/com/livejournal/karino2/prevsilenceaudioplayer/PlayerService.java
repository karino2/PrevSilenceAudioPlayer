package com.livejournal.karino2.prevsilenceaudioplayer;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.os.*;
import android.os.Process;
import android.widget.Toast;

import java.io.IOException;

public class PlayerService extends Service {
    private static final String ACTION_PLAY = "com.livejournal.karino2.prevsilenceaudioplayer.action.PLAY";
    private static final String ACTION_STOP = "com.livejournal.karino2.prevsilenceaudioplayer.action.STOP";

    private static final String EXTRA_PARAM_FILE_PATH = "com.livejournal.karino2.prevsilenceaudioplayer.extra.PATH";


    AudioPlayer audioPlayer = new AudioPlayer();
    Thread playerThread = null;

    /**
     * Starts this service to perform action PLAY audio with the given file. If
     * the service is already performing a task this action will be queued.
     */
    public static void startActionPlay(Context context, String audioFilePath) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra(EXTRA_PARAM_FILE_PATH, audioFilePath);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action STOP audio. If
     * the service is already performing a task this action will be queued.
     */
    public static void startActionStop(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }




    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                handleActionPlay(intent.getStringExtra(EXTRA_PARAM_FILE_PATH));
                return START_STICKY;
            } else if (ACTION_STOP.equals(action)) {
                handleActionStop();
                return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    void showMessage(String msg)
    {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    Handler handler = new Handler();

    /**
     * Handle action PLAY in the provided background thread with the provided
     * parameters.
     */
    private void handleActionPlay(String audioFilePath)  {
        try {
            if(playerThread == null) {
              audioPlayer.setAudioPath(audioFilePath);
              playerThread =  new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            audioPlayer.play();
                        } catch (IOException e) {
                            final String msg = e.getMessage();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showMessage("play throw exception: " + msg);
                                }
                            });
                        }
                    }
                });
                // TODO: set priority.
                // android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                // playerThread.setPriority();
                playerThread.start();
            } else {
                audioPlayer.playAudio(audioFilePath);
            }
        } catch (IOException e) {
            showMessage("play fail: " + e.getMessage());
        }
    }

    /**
     * Handle action STOP in the provided background thread with the provided
     * parameters.
     */
    private void handleActionStop() {
        audioPlayer.requestStop();
    }
}
