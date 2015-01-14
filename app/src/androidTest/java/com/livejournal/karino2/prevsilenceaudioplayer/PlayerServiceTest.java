package com.livejournal.karino2.prevsilenceaudioplayer;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.test.ServiceTestCase;
import android.util.Log;

import java.util.List;

/**
 * Created by karino on 1/14/15.
 */
public class PlayerServiceTest extends ServiceTestCase<PlayerService> {
    /**
     * Constructor
     *
     * @param serviceClass The type of the service under test.
     */
    public PlayerServiceTest(Class<PlayerService> serviceClass) {
        super(serviceClass);
    }

    public PlayerServiceTest() {
        super(PlayerService.class);
    }

    // this test is fail
    /*
    public void testUriSupposition() {
        Uri expect = Uri.parse("file:///storage/extSdCard/");
        Uri actual = Uri.parse("/storage/extSdCard/");
        assertEquals(expect, actual);
    }
        */

    // can't run because getContentResolver() return null. (not confirmed).
        /*
    public void testQuery() {
                ContentResolver resolver = getContext().getContentResolver();
        Cursor test = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA
        }, MediaStore.Audio.Media.DATA + " LIKE /storage/extSdCard/%", null, null);
        if(test.moveToFirst()) {
            String d1 = test.getString(1);
            if(test.moveToNext()) {
                d1 = test.getString(1);
                if(test.moveToNext()) {
                    d1 = test.getString(1);
                    if(test.moveToNext()) {
                        d1 = test.getString(1);
                        Log.d("Test", d1);
                    }
                }
            }
        }
        test.close();

    }

         */

    public void testGetParentUri_fileScheme() {
        PlayerService service = new PlayerService();
        Uri input = Uri.parse("file:///storage/extSdCard/test.mp3");
        Uri expect = Uri.parse("file:///storage/extSdCard/");

        Uri actual = service.getParentUri(input);
        assertEquals(expect, actual);




    }
}
