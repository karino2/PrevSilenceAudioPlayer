package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


public class PlayerActivity extends ActionBarActivity {

    private SharedPreferences getPref() {
        return getSharedPreferences("pref", MODE_PRIVATE);
    }

    private String getLastFile() {
        return getPref().getString("LAST_PLAY", "");
    }

    void showMessage(String msg)
    {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    static final int REQUEST_GET_AUDIO = 1;

    Handler handler = new Handler();


    public static boolean s_isCreated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        s_isCreated = true;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_player);

        Intent intent = getIntent();
        if(intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW))
        {
            Uri uri = intent.getData();
            String path = uri.toString(); /* uri.getPath() */
            setPathToEditText(path);
            PlayerService.startActionPlay(this, uri.toString());
        } else {
            setPathToEditText(getLastFile());
            if("".equals(getLastFile())) {
                postChooseAudio();
            }
        }


        findViewById(R.id.buttonPlay).setOnClickListener(new View.OnClickListener() {
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

        findViewById(R.id.buttonPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionPrev(PlayerActivity.this);
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

    private void setPathToEditText(String path) {
        ((EditText)findViewById(R.id.editTextPath)).setText(path);
    }

    @Override
    protected void onDestroy() {
        s_isCreated = false;
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
                    String path = data.getData().getPath();
                    setPathToEditText(path);
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
