package com.livejournal.karino2.prevsilenceaudioplayer;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;


public class PlayerActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);


        findViewById(R.id.buttonPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService.startActionPlay(PlayerActivity.this, ((EditText)findViewById(R.id.editTextPath)).getText().toString());
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
