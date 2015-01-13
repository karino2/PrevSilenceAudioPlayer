package com.livejournal.karino2.prevsilenceaudioplayer;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.media.AudioManager;
import android.os.*;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

public class PlayerService extends Service {
    private static final String ACTION_PLAY = "com.livejournal.karino2.prevsilenceaudioplayer.action.PLAY";
    private static final String ACTION_STOP = "com.livejournal.karino2.prevsilenceaudioplayer.action.STOP";
    private static final String ACTION_PREV = "com.livejournal.karino2.prevsilenceaudioplayer.action.PREV";
    private static final String ACTION_QUIT = "com.livejournal.karino2.prevsilenceaudioplayer.action.QUIT";

    private static final String EXTRA_PARAM_FILE_PATH = "com.livejournal.karino2.prevsilenceaudioplayer.extra.PATH";


    AudioPlayer audioPlayer = new AudioPlayer(new AudioPlayer.RestartListener() {
        @Override
        public void requestRestart() {
            // delayed for avoid isRunning overwrite for finally clause.
            handler.post(new Runnable() {
                @Override
                public void run() {
                    startPlayThread();
                }
            });
        }
    });
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

    public static void startActionPrevWithDelay(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PREV);
        intent.putExtra("DELAY", true);
        context.startService(intent);
    }


    public static void startActionPrev(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PREV);
        context.startService(intent);
    }

    public static void startActionQuit(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_QUIT);
        context.startService(intent);
    }

    boolean receiverRegistered = false;
    ComponentName receiverName;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(!receiverRegistered) {
            receiverRegistered = true;

            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            receiverName = new ComponentName(this, RemoteControlReceiver.class);
            am.registerMediaButtonEventReceiver(receiverName);


            /*

            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            */
            /*
            int result = am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                                                  @Override
                                                  public void onAudioFocusChange(int focusChange) {

                                                  }
                                              },
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                showMessage("can't gain audio focus");
            }
            */
        }


        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                handleActionPlay(intent.getStringExtra(EXTRA_PARAM_FILE_PATH));
                return START_STICKY;
            } else if (ACTION_STOP.equals(action)) {
                handleActionStop();
                return START_NOT_STICKY;
            } else if(ACTION_PREV.equals(action)) {
                handleActionPrev(intent.getBooleanExtra("DELAY", false));
                return START_STICKY;
            } else if(ACTION_QUIT.equals(action)) {
                audioPlayer.requestStop();
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        showMessage("unknown start command.");
        return START_NOT_STICKY;
    }

    private void handleActionPrev(boolean withDelay) {
        Log.d("PrevSilence", "handlePrev0");
        audioPlayer.requestPrev(withDelay);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    void showDebugMessage(String msg)
    {
        Log.d("PrevSilence", "DEB: " + msg);
        showMessage(msg);
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
                startPlayThread();
            } else {
                audioPlayer.playAudio(audioFilePath);
            }
        } catch (IOException e) {
            showDebugMessage("play fail: " + e.getMessage());
        }
    }

    private void startPlayThread() {
        playerThread =  new Thread(new Runnable() {
              @Override
              public void run() {
                  Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                  try {
                      audioPlayer.play();
                  } catch (IOException e) {
                      final String msg = e.getMessage();
                      handler.post(new Runnable() {
                          @Override
                          public void run() {
                              showDebugMessage("play throw exception: " + msg);
                          }
                      });
                  }
              }
          });
        playerThread.start();
    }

    @Override
    public void onDestroy() {
        if(receiverRegistered) {
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            am.unregisterMediaButtonEventReceiver(receiverName);
        }
        super.onDestroy();
    }

    /**
     * Handle action STOP in the provided background thread with the provided
     * parameters.
     */
    private void handleActionStop() {
        if(audioPlayer.isRunning()) {
            audioPlayer.requestStop();
        }
    }
}
