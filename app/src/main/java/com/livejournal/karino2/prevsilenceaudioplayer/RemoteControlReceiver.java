package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class RemoteControlReceiver extends BroadcastReceiver {
    static boolean is_registerd = false;
    static ComponentName receiverName;
    public static void ensureReceiverRegistered(Context context) {
        if(!is_registerd) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            receiverName  = new ComponentName(context, RemoteControlReceiver.class);;
            am.registerMediaButtonEventReceiver(receiverName);
            is_registerd = true;
        }
    }
    public static void unregisterReceiver(Context context) {
        if(is_registerd) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            am.unregisterMediaButtonEventReceiver(receiverName);
            is_registerd = false;
        }

    }


    public RemoteControlReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Log.d("BlueTooth", "receive");
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            // Log.d("BlueTooth", event.toString());
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return; // only handle key down.
            }
            if (KeyEvent.KEYCODE_MEDIA_PLAY == event.getKeyCode() ||
                    KeyEvent.KEYCODE_MEDIA_PAUSE == event.getKeyCode()) {
                PlayerService.startActionPlayOrPause(context, true);
                abortBroadcast();
                // showDebugMessage(context, "play or pause");
                return;
            } else if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == event.getKeyCode()) {
                PlayerService.startActionPrevWithDelay(context);
                abortBroadcast();
                // showDebugMessage(context, "prev received");
                return;
            } else if (KeyEvent.KEYCODE_MEDIA_NEXT == event.getKeyCode()) {
                PlayerService.startActionNext(context, true);
                abortBroadcast();
                // showDebugMessage(context, "next received");
                return;
            }
        }
    }

    static void showDebugMessage(Context context, String msg) {
        Log.d("BlueTooth", msg);
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }
}
