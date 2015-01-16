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
    private static final String ACTION_NEXT = "com.livejournal.karino2.prevsilenceaudioplayer.action.NEXT";
    private static final String ACTION_QUIT = "com.livejournal.karino2.prevsilenceaudioplayer.action.QUIT";
    private static final String ACTION_TOGGLE_PAUSE = "com.livejournal.karino2.prevsilenceaudioplayer.action.TOGGLE_PAUSE";

    private static final String EXTRA_PARAM_FILE_PATH = "com.livejournal.karino2.prevsilenceaudioplayer.extra.PATH";

    public static class PlayFileChangedEvent {
        Uri file;
        public Uri getFile() {
            return file;
        }
        public PlayFileChangedEvent(Uri filePath) {
            file = filePath;
        }
    }

    public static class PlayStateEvent {
        public PlayStateEvent(){}
    }
    public static class PauseStateEvent {
        public PauseStateEvent(){}
    }

    boolean duringWait = false;
    final int MEDIABUTTON_WAIT_DELAY = 500; // ms

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
        public void requestNext() {
            postPlayNext();
        }

        @Override
        public void requestMediaButtonWait() {
            duringWait = true;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    duringWait = false;
                    // always there are an event after MEDIABUTTON_WAIT.
                    startPlayThread();
                }
            }, MEDIABUTTON_WAIT_DELAY);
        }

        @Override
        public void reachEnd() {
            postPlayNext();
        }
    });

    private void postPlayNext() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                playNext();
            }
        });
    }


    private String findNextOrPrev(Uri origin, String order) {
        if(!origin.getScheme().equals("file"))
        {
            Log.d("PrevSilent", "only support file:// for nextOrPrev");
            return null;
        }
        ContentResolver resolver = getContentResolver();

        Uri parent = getParentUri(origin);

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
                MediaStore.Audio.Media.DATA + " " + order
        );
        try {
            if(!cursor.moveToFirst()) {
                Log.d("PrevSilence", "No music file found. Where is current playing file?");
                return null;
            }
            do {
                if(cursor.getString(1).equals(origin.getPath())) {
                    if(!cursor.moveToNext()) {
                        Log.d("PrevSilence", "This file is last. No next file.");
                        return null;
                    }
                    String nextPath = cursor.getString(1);
                    // showMessage("Next file is: " + nextPath);
                    String nextUriStr = "file://" + nextPath;
                    return nextUriStr;
                }
            }while(cursor.moveToNext());
            Log.d("PrevSilence", "Original audio file not found. where is there?");
            return null;
        } finally {
            cursor.close();
        }

    }

    private void playNextOrPrev(String order) {
        String nextPath = findNextOrPrev(Uri.parse(getLastFile()), order);
        if(nextPath != null) {
            try {
                startPlayThreadWithFile(nextPath);
                showMessage(nextPath);
            } catch (IOException e) {
                showMessage("play nextOrPrev fail: " + e.getMessage());
                return;
            }
            saveLastFile(nextPath); // setDataSource is succeeded. So save here is not so bad.
            BusProvider.getInstance().post(new PlayFileChangedEvent(Uri.parse(nextPath)));
        }
    }

    private void playNext() {
        playNextOrPrev("ASC");
    }

    // just set to previous file, not play.
    private void gotoPrev() throws IOException {
        String nextPath = findNextOrPrev(Uri.parse(getLastFile()), "DESC");
        audioPlayer.setAudioPath(nextPath);
        saveLastFile(nextPath);
        BusProvider.getInstance().post(new PlayFileChangedEvent(Uri.parse(nextPath)));
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

    public static void startActionPlayOrPause(Context context, boolean withDelay) {
        String lastFile = s_getLastFile(context);
        if("".equals(lastFile)) {
            // TODO: use event and kick GET_CONTENT intent from Activity.
            s_showMessage(context, "No audio set. Please choose audio first.");
            return;
        }
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_TOGGLE_PAUSE);
        if(withDelay)
            intent.putExtra("DELAY", true);
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

    public static void startActionNext(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_NEXT);
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



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        audioPlayer.setContext(this);
        audioPlayer.setLastAudioPath(getLastFile());

        RemoteControlReceiver.ensureReceiverRegistered(this);



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
                handleActionTogglePause(intent.getBooleanExtra("DELAY", false));
                return START_STICKY;
            } else if (ACTION_NEXT.equals(action)) {
                handleActionNext();
                return START_STICKY;
            }
        }
        showMessage("unknown start command.");
        return START_NOT_STICKY;
    }

    private void handleActionNext() {
        if(isPlayerRunning()) {
            audioPlayer.requestNext();
        } else {
            playNext();
        }
    }

    private void handleActionTogglePause(boolean withDelay) {
        if(isPlayerRunning()) {
            BusProvider.getInstance().post(new PauseStateEvent());
            audioPlayer.requestPause();
        } else {
            BusProvider.getInstance().post(new PlayStateEvent());
            if(withDelay)
                audioPlayer.pushMediaButtonWaitCommand();
            startPlayThread();
        }
    }


    private void handleActionPrev(boolean withDelay) {
        if(isPlayerRunning()) {
            audioPlayer.requestPrev(withDelay);
        } else if(!audioPlayer.atHead()) {
            audioPlayer.gotoHead();
        } else {
            try {
                gotoPrev();
            } catch (IOException e) {
                showMessage("Fail to setup previous file: " + e.getMessage());
            }
        }
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
            if(!isPlayerRunning()) {
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
        super.onDestroy();
    }

    /**
     * Handle action STOP in the provided background thread with the provided
     * parameters.
     */
    private void handleActionStop() {
        if(isPlayerRunning()) {
            audioPlayer.requestStop();
        }
    }

    private boolean isPlayerRunning() {
        return duringWait || audioPlayer.isRunning();
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
