package mindustry.game.griefprevention;

import arc.struct.Array;
import mindustry.entities.type.Player;

import java.lang.ref.WeakReference;

/** Holds short ids ("refs") for players */
public class RefList {
    public Array<WeakReference<Player>> list = new Array<>();
    // why yes, i will put two stacks in one class
    /** current free ref entries */
    private int[] free = new int[16];
    private int freePos = 0;
    private int cleanupCount = 0;
    /** amount of calls to getRef() before cleanup */
    private static final int cleanupInterval = 25;

    public void reset() {
        cleanupCount = 0;
        free = new int[16];
        freePos = 0;
        list.clear();
    }

    public void cleanup() {
        cleanupCount = 0;
        for (int i = 0; i < list.size; i++) {
            WeakReference<Player> wr = list.get(i);
            if (wr == null) continue;
            Player p = wr.get();
            if (p == null) {
                // player object was garbage collected, remove
                list.set(i, null);
                if (freePos == free.length) resizeFree(free.length << 1);
                free[freePos++] = i;
            }
        }
        if (list.size == 0) reset();
    }

    public void resizeFree(int newLength) {
        if (newLength < freePos) throw new RuntimeException("Array too short");
        int[] oldFree = free;
        free = new int[newLength];
        System.arraycopy(oldFree, 0, free, 0, freePos);
    }

    public int get(Player p) {
        if (++cleanupCount > cleanupInterval) cleanup();
        if (p.ref > -1) return p.ref;
        // create new ref
        int ref;
        if (freePos > 0) {
            ref = free[freePos - 1];
            free[freePos] = -1;
            freePos--;
        } else {
            list.add(null);
            ref = list.size - 1;
        }
        list.set(ref, new WeakReference<>(p));
        p.ref = ref;
        return ref;
    }

    public Player get(int ref) {
        if (ref > list.size) return null;
        WeakReference<Player> wr = list.get(ref);
        if (wr == null) return null;
        return wr.get();
    }
}

