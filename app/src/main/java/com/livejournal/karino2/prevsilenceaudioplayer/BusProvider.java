package com.livejournal.karino2.prevsilenceaudioplayer;

import com.squareup.otto.Bus;

/**
 * Created by karino on 1/15/15.
 */
public class BusProvider {
    private static final Bus BUS = new Bus();

    public static Bus getInstance() {
        return BUS;
    }

    private BusProvider() {
    }
}
