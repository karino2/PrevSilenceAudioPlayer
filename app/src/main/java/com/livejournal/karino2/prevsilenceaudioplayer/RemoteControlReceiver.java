package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class RemoteControlReceiver extends BroadcastReceiver {
    static boolean isServiceRunning = false;

    public static void setServiceRunning(boolean isRunning) {
        isServiceRunning = isRunning;
    }


    public RemoteControlReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(isServiceRunning) {
            Log.d("BlueTooth", "receive");
            // Toast.makeText(context, "onrecieve called. ", Toast.LENGTH_LONG).show();
            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return; // only handle key down.
                }
                if (KeyEvent.KEYCODE_MEDIA_PLAY == event.getKeyCode()) {
                    // NYI.
                } else if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == event.getKeyCode()) {
                    PlayerService.startActionPrevWithDelay(context);
                    abortBroadcast();
                    Toast.makeText(context, "prev recived", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }
}
