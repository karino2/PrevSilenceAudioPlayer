package com.livejournal.karino2.prevsilenceaudioplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.List;

public class PlayerService extends Service {
    private static final String ACTION_PLAY = "com.livejournal.karino2.prevsilenceaudioplayer.action.PLAY";
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

    final static int NOTIFICATION_ID = R.layout.notification;

    public static class PlayPauseStateEvent {
        public static PlayPauseStateEvent createPlayStateEvent() { return new PlayPauseStateEvent(false) ; }
        public static PlayPauseStateEvent createPauseStateEvent() { return new PlayPauseStateEvent(true) ; }
        public PlayPauseStateEvent(boolean pauseState){ isPause = pauseState; }
        boolean isPause;

        public boolean isPause() {
            return isPause;
        }
    }
    public static class PauseStateEvent {
        public PauseStateEvent(){}
    }
    public static class NoAudioEvent {
        public NoAudioEvent(){}
    }

    boolean duringWait = false;
    int mediaButtonWaitDelay; // ms

    public void newAudioPlayer() {
        audioPlayer = new AudioPlayer(new AudioPlayer.StateChangedListener() {
            @Override
            public void requestRestart() {
                // delayed for avoid isInsidePlayLoop overwrite for finally clause.
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        startPlayThread();
                    }
                });
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
                }, mediaButtonWaitDelay);
            }

            @Override
            public void reachEnd() {
                postPlayNext();
            }
        },
                Math.max(1, Long.valueOf(getPref().getString("SILENCE_INTENSITY_THRESHOLD", "1000"))),
                Math.max(1, Long.valueOf(getPref().getString("SILENCE_DURATION_THRESHOLD", "100")))
        );
    }

    AudioPlayer audioPlayer;

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
            // Log.d("PrevSilent", "only support file:// for nextOrPrev");
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
                        // Log.d("PrevSilence", "This file is last. No next file.");
                        return null;
                    }
                    String nextPath = cursor.getString(1);
                    // showMessage("Next file is: " + nextPath);
                    return "file://" + nextPath;
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
        } else {
            BusProvider.getInstance().post(PlayPauseStateEvent.createPauseStateEvent());
        }
    }

    private void playNext() {
        playNextOrPrev("ASC");
    }

    // just set to previous file, not play.
    private void gotoPrev() throws IOException {
        gotoPrevOrNext("DESC");
    }

    private void gotoNext() throws IOException {
        gotoPrevOrNext("ASC");
    }


    private void gotoPrevOrNext(String order) throws IOException {
        String nextPath = findNextOrPrev(Uri.parse(getLastFile()), order);
        if(nextPath == null) {
            return; // no next file, do nothing.
        }
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
            BusProvider.getInstance().post(new NoAudioEvent());
            return;
        }
        Intent intent = s_createTogglePauseIntent(context);
        if(withDelay)
            intent.putExtra("DELAY", true);
        context.startService(intent);
    }

    public static Intent s_createTogglePauseIntent(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_TOGGLE_PAUSE);
        return intent;
    }

    public static void startActionPrevWithDelay(Context context) {
        Intent intent = s_createPrevIntent(context);
        intent.putExtra("DELAY", true);
        context.startService(intent);
    }

    public static Intent s_createPrevIntent(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_PREV);
        return intent;
    }


    public static void startActionPrev(Context context) {
        Intent intent = s_createPrevIntent(context);
        context.startService(intent);
    }

    public static void startActionQuit(Context context) {
        Intent intent = s_createQuitIntent(context);
        context.startService(intent);
    }

    public static Intent s_createQuitIntent(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_QUIT);
        return intent;
    }

    public static void startActionNext(Context context, boolean withDelay) {
        Intent intent = s_createNextIntent(context);
        intent.putExtra("DELAY", withDelay);
        context.startService(intent);
    }

    public static Intent s_createNextIntent(Context context) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(ACTION_NEXT);
        return intent;
    }


    private static String s_getLastFile(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getString("LAST_PLAY", "");
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


        if(notificationView == null)
        {
            showNotification();
        }


            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            int result = am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                                                  @Override
                                                  public void onAudioFocusChange(int focusChange) {
                                                        Log.d("PrevSilence", "Focus changed: " + focusChange);
                                                  }
                                              },
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                showMessage("can't gain audio focus");
            }


        if (intent != null) {
            // RemoteControlReceiver.ensureReceiverRegistered(this);
            final String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                handleActionPlay(intent.getStringExtra(EXTRA_PARAM_FILE_PATH));
                return START_STICKY;
            } else if(ACTION_PREV.equals(action)) {
                handleActionPrev(intent.getBooleanExtra("DELAY", false));
                return START_STICKY;
            } else if(ACTION_QUIT.equals(action)) {
                audioPlayer.requestStop();
                stopSelf();
                RemoteControlReceiver.unregisterReceiver(this);
                return START_NOT_STICKY;
            } else if(ACTION_TOGGLE_PAUSE.equals(action)) {
                handleActionTogglePause(intent.getBooleanExtra("DELAY", false));
                return START_STICKY;
            } else if (ACTION_NEXT.equals(action)) {
                handleActionNext(intent.getBooleanExtra("DELAY", false));
                return START_STICKY;
            }
        }
        // for recreate case. sometime static variable is not yet cleared, but media button receiver is unregistered. We register anyway.
        // RemoteControlReceiver.forthRegisterReceiver(this);
        return START_STICKY;
    }

    private void handleActionNext(boolean withDelay) {
        if(isPlaying()) {
            audioPlayer.requestNext(withDelay);
        } else {
            try {
                gotoNext();
            } catch (IOException e) {
                showMessage("Fail to setup next file: " + e.getMessage());
            }
        }
    }

    private void handleActionTogglePause(boolean withDelay) {
        if(isPlaying()) {
            BusProvider.getInstance().post(PlayPauseStateEvent.createPauseStateEvent());
            audioPlayer.requestPause();
        } else {
            BusProvider.getInstance().post(PlayPauseStateEvent.createPlayStateEvent());
            if(withDelay)
                audioPlayer.pushMediaButtonWaitCommand();
            startPlayThread();
        }
    }


    private void handleActionPrev(boolean withDelay) {
        if(isPlaying()) {
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
            if(!audioPlayer.isInsidePlayLoop()) {
                startPlayThreadWithFile(audioFilePath);
            } else {
                audioPlayer.requestPlayAudio(audioFilePath);
            }
            saveLastFile(audioFilePath); // write if succeed.
            BusProvider.getInstance().post(PlayPauseStateEvent.createPlayStateEvent());
            BusProvider.getInstance().post(new PlayFileChangedEvent(Uri.parse(audioFilePath)));
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

    SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    @Override
    public void onCreate() {
        super.onCreate();
        BusProvider.getInstance().register(this);
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if("MEDIA_BUTTON_WAIT".equals(key)) {
                    mediaButtonWaitDelay = Math.max(0, Integer.valueOf(getPref().getString("MEDIA_BUTTON_WAIT", "650")));
                    return;
                }
                if("LAST_PLAY".equals(key))
                    return;
                updatePreference();
            }
        };
        getPref().registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        newAudioPlayer();
        mediaButtonWaitDelay = Math.max(0, Integer.valueOf(getPref().getString("MEDIA_BUTTON_WAIT", "650")));
    }

    private void updatePreference() {
        // caution! should not call any callback after this method!
        if(audioPlayer.isInsidePlayLoop()) {
            audioPlayer.requestStop();
            // this is not Pause, but currently no difference. I just re-use the same event for a while.
            BusProvider.getInstance().post(PlayPauseStateEvent.createPauseStateEvent());
        }

        newAudioPlayer();
    }

    public SharedPreferences getPref() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onDestroy() {
        BusProvider.getInstance().unregister(this);
        hideNotification();
        super.onDestroy();
    }

    private boolean isPlaying() {
        return duringWait || audioPlayer.isPlaying();
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


    void hideNotification() {
        NotificationManager notificationManager = getNotificationManager();
        notificationManager.cancel(NOTIFICATION_ID);

        notificationView = null;

    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Produce
    public PlayPauseStateEvent producePlayPauseEvent() {
        if((audioPlayer != null) && audioPlayer.isPlaying()) {
            return PlayPauseStateEvent.createPlayStateEvent();
        }
        return PlayPauseStateEvent.createPauseStateEvent();
    }

    @Subscribe
    public void onPlayFileChanged(PlayerService.PlayFileChangedEvent event) {
        if(notificationView != null) {
            notificationView.setTextViewText(R.id.textViewNotificationTitle, findDisplayNameFromUriStr(event.getFile().toString()));
            getNotificationManager().notify(NOTIFICATION_ID, notification);
        }
    }

    @Subscribe
    public void onPlayOrPauseState(PlayPauseStateEvent event) {
        if(notificationView != null) {
            if(event.isPause()) {
                notificationView.setImageViewResource(R.id.imageButtonNotificationPlayOrPause, R.drawable.button_play_small);
            } else {
                notificationView.setImageViewResource(R.id.imageButtonNotificationPlayOrPause, R.drawable.button_pause_small);
            }
            getNotificationManager().notify(NOTIFICATION_ID, notification);
        }
    }

    RemoteViews notificationView;
    Notification notification;

    void showNotification() {
        String title = findDisplayNameFromUriStr(getLastFile());

        PendingIntent ppauseIntent = createPendingIntent(s_createTogglePauseIntent(this));
        PendingIntent quitIntent = createPendingIntent(s_createQuitIntent(this));
        PendingIntent prevIntent = createPendingIntent(s_createPrevIntent(this));
        PendingIntent nextIntent = createPendingIntent(s_createNextIntent(this));



        notificationView =  new RemoteViews(getPackageName(), R.layout.notification);
        notificationView.setTextViewText(R.id.textViewNotificationTitle, title);
        notificationView.setOnClickPendingIntent(R.id.imageButtonNotificationPlayOrPause, ppauseIntent);
        notificationView.setOnClickPendingIntent(R.id.imageButtonNotificationCollapse, quitIntent);
        notificationView.setOnClickPendingIntent(R.id.imageButtonNotificationPrev, prevIntent);
        notificationView.setOnClickPendingIntent(R.id.imageButtonNotificationNext, nextIntent);



        notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.notif_icon)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContent(notificationView)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private String findDisplayNameFromUriStr(String lastFileStr) {
        String title;
        if(lastFileStr.equals("")) {
            title ="No file selected.";
        } else {
            title = PlayerActivity.s_findDisplayNameFromUri(this, Uri.parse(lastFileStr));
        }
        return title;
    }

    private PendingIntent createPendingIntent(Intent intent) {
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
