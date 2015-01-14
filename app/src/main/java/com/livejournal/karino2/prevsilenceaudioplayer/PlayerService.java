package com.livejournal.karino2.prevsilenceaudioplayer;

import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PlayerService extends Service {
    private static final String ACTION_PLAY = "com.livejournal.karino2.prevsilenceaudioplayer.action.PLAY";
    private static final String ACTION_STOP = "com.livejournal.karino2.prevsilenceaudioplayer.action.STOP";
    private static final String ACTION_PREV = "com.livejournal.karino2.prevsilenceaudioplayer.action.PREV";
    private static final String ACTION_QUIT = "com.livejournal.karino2.prevsilenceaudioplayer.action.QUIT";
    private static final String ACTION_TOGGLE_PAUSE = "com.livejournal.karino2.prevsilenceaudioplayer.action.TOGGLE_PAUSE";

    private static final String EXTRA_PARAM_FILE_PATH = "com.livejournal.karino2.prevsilenceaudioplayer.extra.PATH";


    AudioPlayer audioPlayer = new AudioPlayer(new AudioPlayer.StateChangedListener() {
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

        @Override
        public void reachEnd() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    playNext();
                }
            });
        }
    });

    private void playNext() {
        ContentResolver resolver = getContentResolver();
        Uri lastFile = Uri.parse(getLastFile());
        if(!lastFile.getScheme().equals("file"))
        {
            Log.d("PrevSilent", "only support file:// for gotoNext");
            return;
        }

        Uri parent = getParentUri(lastFile);

        // TODO: This query contains sub folder too.
        // assume file: for a while.
        Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DATA
                },
                /*
                "? LIKE ?",
                new String[] {
                        MediaStore.Audio.Media.DATA,
                        parent.toString()+"%"
                },
                */
                MediaStore.Audio.Media.DATA + " LIKE ?",
                new String[] {
                        parent.getPath()+"%"
                },
                MediaStore.Audio.Media.DATA + " ASC"
                );
        try {
            if(!cursor.moveToFirst()) {
                Log.d("PrevSilence", "No music file found. Where is current playing file?");
                return;
            }
            do {
                if(cursor.getString(1).equals(lastFile.getPath())) {
                    if(!cursor.moveToNext()) {
                        Log.d("PrevSilence", "This file is last. No next file.");
                        return;
                    }
                    String nextPath = cursor.getString(1);
                    showMessage("Next file is: " + nextPath);
                    String nextUriStr = "file://" + nextPath;
                    startPlayThreadWithFile(nextUriStr);
                    saveLastFile(nextUriStr); // setDataSource is succeeded. So save here is not so bad.
                    return;
                }
            }while(cursor.moveToNext());
            Log.d("PrevSilence", "Original audio file not found. where is there?");
        } catch (IOException e) {
            showMessage("fail to play next file: " + e.getMessage());
        } finally {
            cursor.close();
        }
    }

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

    public static void startActionPlayOrPause(Context context) {
        String lastFile = s_getLastFile(context);
        if("".equals(lastFile)) {
            s_showMessage(context, "No audio set. Please choose audio first.");
            return;
        }
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_TOGGLE_PAUSE);
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

    private SharedPreferences getPref() {
        return getSharedPreferences("pref", MODE_PRIVATE);
    }

    private static String s_getLastFile(Context ctx) {
        return ctx.getSharedPreferences("pref", MODE_PRIVATE).getString("LAST_PLAY", "");
    }

    private String getLastFile() {
        return getPref().getString("LAST_PLAY", "");
    }

    private void saveLastFile(String path) {
        getPref().edit()
                .putString("LAST_PLAY", path)
                .commit();
    }


    boolean receiverRegistered = false;
    ComponentName receiverName;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        audioPlayer.setContext(this);
        audioPlayer.setLastAudioPath(getLastFile());

        if(!receiverRegistered) {
            receiverRegistered = true;

            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            receiverName = new ComponentName(this, RemoteControlReceiver.class);
            am.registerMediaButtonEventReceiver(receiverName);
            RemoteControlReceiver.setServiceRunning(true);


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
            } else if(ACTION_TOGGLE_PAUSE.equals(action)) {
                handleActionTogglePause();
                return START_STICKY;
            }
        }
        showMessage("unknown start command.");
        return START_NOT_STICKY;
    }

    private void handleActionTogglePause() {
        if(audioPlayer.isRunning()) {
            audioPlayer.requestPause();
        } else {
            startPlayThread();
        }
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

    static void s_showMessage(Context ctx, String msg)
    {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    void showMessage(String msg)
    {
        s_showMessage(getApplicationContext(), msg);
    }

    Handler handler = new Handler();

    /**
     * Handle action PLAY in the provided background thread with the provided
     * parameters.
     */
    private void handleActionPlay(String audioFilePath)  {
        try {
            if(!audioPlayer.isRunning()) {
                startPlayThreadWithFile(audioFilePath);
            } else {
                audioPlayer.playAudio(audioFilePath);
            }
            saveLastFile(audioFilePath); // write if succeed.
        } catch (IOException e) {
            showDebugMessage("play fail: " + e.getMessage());
        }
    }

    private void startPlayThreadWithFile(String audioFilePath) throws IOException {
        audioPlayer.setAudioPath(audioFilePath);
        startPlayThread();
    }

    private void startPlayThread() {
        playerThread =  new Thread(new Runnable() {
              @Override
              public void run() {
                  Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                  try {
                      audioPlayer.play();
                  } catch (IOException e) {
                      postShowDebugMessage("play throw IO exception: " + e.getMessage());
                  } catch (IllegalArgumentException ie) {
                      postShowDebugMessage("play throw IllArg exception: " + ie.getMessage());

                  }
              }
          });
        playerThread.start();
    }

    private void postShowDebugMessage(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                showDebugMessage(msg);
            }
        });
    }

    @Override
    public void onDestroy() {
        if(receiverRegistered) {
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            am.unregisterMediaButtonEventReceiver(receiverName);
            RemoteControlReceiver.setServiceRunning(false);
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

    public Uri getParentUri(Uri input) {
        List<String> pathSegments = input.getPathSegments();

        StringBuilder builder = new StringBuilder();
        builder.append(input.getScheme());
        builder.append("://");
        builder.append(input.getHost());
        builder.append("/");
        for(int i = 0; i < pathSegments.size()-1; i++) {
            builder.append(pathSegments.get(i));
            builder.append("/");
        }

        return Uri.parse(builder.toString());
    }
}
