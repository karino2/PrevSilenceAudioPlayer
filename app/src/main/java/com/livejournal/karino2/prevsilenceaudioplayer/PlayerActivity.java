package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Subscribe;


public class PlayerActivity extends ActionBarActivity {

    private SharedPreferences getPref() {
        return getSharedPreferences("pref", MODE_PRIVATE);
    }

    private String getLastFile() {
        return getPref().getString("LAST_PLAY", "");
    }

    private void clearLastFile() {
        getPref().edit()
                .putString("LAST_PLAY", "")
                .commit();
    }

    void showMessage(String msg)
    {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    static final int REQUEST_GET_AUDIO = 1;

    Handler handler = new Handler();

    @Subscribe
    public void onPlayFileChanged(PlayerService.PlayFileChangedEvent event) {
        updateAudioDisplayNameFromUriString(event.getFile().toString());
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RemoteControlReceiver.ensureReceiverRegistered(this);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_player);

        setTitle(R.string.main_title);

        Intent intent = getIntent();
        if(intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW))
        {
            Uri uri = intent.getData();
            String path = uri.toString(); /* uri.getPath() */
            updateAudioDisplayNameFromUriString(path);
            PlayerService.startActionPlay(this, uri.toString());
        } else {
            updateAudioDisplayNameFromUriString(getLastFile());
            if("".equals(getLastFile())) {
                postChooseAudio();
            }
        }


        /*
        findViewById(R.id.imageButtonPlayOrPause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = ((EditText)findViewById(R.id.editTextPath)).getText().toString();
                if("".equals(path)) {
                    showMessage("Please specify audio file.");
                    return;
                }
                PlayerService.startActionPlay(PlayerActivity.this, path);
            }
        });

        findViewById(R.id.buttonStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionStop(PlayerActivity.this);
            }
        });
        */

        findViewById(R.id.imageButtonPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionPrev(PlayerActivity.this);
            }
        });

        findViewById(R.id.imageButtonNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionNext(PlayerActivity.this);
            }
        });

        findViewById(R.id.imageButtonChoose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseAudioFile();
            }
        });

        findViewById(R.id.buttonClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessage("Clear saved path.");
                clearLastFile();
            }
        });

        findViewById(R.id.imageButtonPlayOrPause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionPlayOrPause(PlayerActivity.this);
            }
        });


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
        showMessage("Choose audio files");
        Intent i = new Intent();
        i.setAction(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        startActivityForResult(i, REQUEST_GET_AUDIO);
    }

    private String findDisplayNameFromUri(Uri uri) {
        ContentResolver resolver = getContentResolver();

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
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_GET_AUDIO:
                if(resultCode == RESULT_OK) {
                    String path = data.getData().toString();
                    updateAudioDisplayNameFromUriString(path);
                    PlayerService.startActionPlay(this, path);
                }
                return;

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_quit) {
            PlayerService.startActionQuit(this);
            finish();
            return true;
        }
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
