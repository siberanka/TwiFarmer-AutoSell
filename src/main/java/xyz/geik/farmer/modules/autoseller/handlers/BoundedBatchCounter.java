package xyz.geik.farmer.modules.autoseller.handlers;

/** Atomic bounded accumulator with a one-way drain transition. */
final class BoundedBatchCounter {
    private long amount;
    private boolean accepting = true;

    BoundedBatchCounter(long initialAmount) {
        if (initialAmount <= 0) throw new IllegalArgumentException("initialAmount must be positive");
        this.amount = initialAmount;
    }

    synchronized boolean add(long addition, long maximum) {
        if (!accepting || addition <= 0 || maximum <= 0 || addition > maximum - amount) {
            return false;
        }
        amount += addition;
        return true;
    }

    synchronized long closeAndGet() {
        if (!accepting) return -1;
        accepting = false;
        return amount;
    }
}
