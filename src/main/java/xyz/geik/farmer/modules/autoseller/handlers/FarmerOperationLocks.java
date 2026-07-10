package xyz.geik.farmer.modules.autoseller.handlers;

import java.util.concurrent.locks.ReentrantLock;

/** Fixed-size striped locks avoid both double-sell races and per-farmer map growth. */
final class FarmerOperationLocks {
    private static final ReentrantLock[] STRIPES = new ReentrantLock[256];

    static {
        for (int index = 0; index < STRIPES.length; index++) {
            STRIPES[index] = new ReentrantLock();
        }
    }

    private FarmerOperationLocks() { }

    static ReentrantLock forFarmer(int farmerId) {
        int spread = farmerId ^ (farmerId >>> 16);
        return STRIPES[spread & (STRIPES.length - 1)];
    }
}
