package com.livejournal.karino2.prevsilenceaudioplayer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.squareup.otto.Subscribe;


public class PlayerActivity extends ActionBarActivity {

    private SharedPreferences getPref() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private String getLastFile() {
        return getPref().getString("LAST_PLAY", "");
    }

    private void clearAllPreferences() {
        getPref().edit()
                .putString("LAST_PLAY", "")
                .putString("MEDIA_BUTTON_WAIT", "650")
                .putString("SILENCE_INTENSITY_THRESHOLD", "1000")
                .putString("SILENCE_DURATION_THRESHOLD", "10")
                .commit();
    }

    void showMessage(String msg)
    {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    static final int REQUEST_GET_AUDIO = 1;
    static final int DIALOG_ID_ABOUT = 2;

    Handler handler = new Handler();

    @Subscribe
    public void onPlayState(PlayerService.PlayStateEvent event) {
        ((ImageButton)findViewById(R.id.imageButtonPlayOrPause)).setImageResource(R.drawable.button_pause);
    }

    @Subscribe
    public void onPauseState(PlayerService.PauseStateEvent event) {
        ((ImageButton)findViewById(R.id.imageButtonPlayOrPause)).setImageResource(R.drawable.button_play);
    }

    @Subscribe
    public void onNoAudio(PlayerService.NoAudioEvent event) {
        postChooseAudio();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BusProvider.getInstance().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        BusProvider.getInstance().unregister(this);
    }


    AdView adView;
    SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RemoteControlReceiver.ensureReceiverRegistered(this);

        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if("LAST_PLAY".equals(key)) {
                    updateAudioDisplayNameFromUriString(getLastFile());
                }

            }
        };
        getPref().registerOnSharedPreferenceChangeListener(preferenceChangeListener);


        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_player);

        setTitle(R.string.main_title);

        Intent intent = getIntent();
        if(intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW))
        {
            Uri uri = intent.getData();
            String path = uri.toString(); /* uri.getPath() */
            PlayerService.startActionPlay(this, uri.toString());
        } else {
            updateAudioDisplayNameFromUriString(getLastFile());
        }

        findViewById(R.id.imageButtonPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionPrev(PlayerActivity.this);
            }
        });

        findViewById(R.id.imageButtonNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionNext(PlayerActivity.this, false);
            }
        });

        findViewById(R.id.imageButtonPlayOrPause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionPlayOrPause(PlayerActivity.this, false);
            }
        });

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                insertAds();
            }
        }, 1000);
    }

    private void insertAds() {
        adView = new AdView(this);
        adView.setAdUnitId( "ca-app-pub-1421062854011986/7191991959" );
        adView.setAdSize(AdSize.SMART_BANNER);
        adView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout layout = (LinearLayout)findViewById(R.id.linearLayoutAds );
        layout.addView(adView);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd( adRequest );
    }

    private void postChooseAudio() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                chooseAudioFile();
            }
        });
    }

    private void chooseAudioFile() {
        showMessage("Choose audio file");
        Intent i = new Intent();
        i.setAction(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        startActivityForResult(i, REQUEST_GET_AUDIO);
    }

    String findDisplayNameFromUri(Uri uri) {
        return s_findDisplayNameFromUri(this, uri);
    }
    public static String s_findDisplayNameFromUri(Context ctx, Uri uri) {
        ContentResolver resolver = ctx.getContentResolver();

        Cursor cursor;
        if(uri.getScheme().equals("file")) {

            cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{
                            MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.DISPLAY_NAME,
                            MediaStore.Audio.Media.DATA
                    },
                    MediaStore.Audio.Media.DATA + " = ?",
                    new String[]{
                            uri.getPath()
                    },
                    null
            );
        }else {
            // may be other possibilities. but I don't know.
            cursor = resolver.query(uri,
                    new String[]{
                            MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.DISPLAY_NAME,
                            MediaStore.Audio.Media.DATA
                    },
                    null,
                    null,
                    null
            );
        }
        try {
            if (!cursor.moveToFirst()) {
                return "No Name";
            }
            // In my audio file, most of title is mojibake. So I don't want to use it.
            // showMessage("title: " + cursor.getString(0));
            return cursor.getString(1);
        } finally {
            cursor.close();
        }
    }


    private void updateAudioDisplayNameFromUriString(String uriStr) {
        if(uriStr.equals("")) {
            updateDisplayName("No audio selected.");
        } else {
            String displayName = findDisplayNameFromUri(Uri.parse(uriStr));
            updateDisplayName(displayName);
        }
    }

    private void updateDisplayName(String displayName) {
        ((TextView)findViewById(R.id.textViewName)).setText(displayName);
    }

    @Override
    protected void onDestroy() {
        getPref().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_GET_AUDIO:
                if(resultCode == RESULT_OK) {
                    String path = data.getData().toString();
                    PlayerService.startActionPlay(this, path);
                }
                return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private AlertDialog createAbout() {
        final WebView webView = new WebView(this);
        webView.loadUrl("file:///android_asset/licenses.html");
        return new AlertDialog.Builder(this).setTitle("About")
                .setView(webView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int whichButton) {
                        dialog.dismiss();
                    }
                }).create();

    }


    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
            case DIALOG_ID_ABOUT:
                return createAbout();
        }
        return super.onCreateDialog(id);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_quit:
                PlayerService.startActionQuit(this);
                finish();
                return true;
            case R.id.action_choose:
                postChooseAudio();
                return true;
            case R.id.action_clear:
                showMessage("Clear saved path.");
                clearAllPreferences();
                return true;
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_about:
                showDialog(DIALOG_ID_ABOUT);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
