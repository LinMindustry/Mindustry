package mindustry.game.griefprevention;

import arc.struct.Array;
import arc.struct.Queue;
import arc.util.Log;
import mindustry.entities.type.Player;
import mindustry.game.griefprevention.Actions.Action;
import mindustry.game.griefprevention.Actions.TileAction;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class ActionLog {
    public static final int maxEntries = 5000;

    public Queue<Action> actions = new Queue<>();

    public void add(Action action) {
        if (actions.size >= maxEntries) actions.removeLast();
        actions.addFirst(action);

        if (griefWarnings.logActions) {
            griefWarnings.sendLocal("[cyan]Debug[] Action: " + action.toString());
            Log.infoTag("antigrief", "Action: " + action.toString());
        }
    }

    public Array<TileAction> getActions(Tile tile) {
        Array<TileAction> results = new Array<>();
        for (Action action : actions) {
            if (action instanceof TileAction) {
                TileAction tileAction = (TileAction) action;
                if (tileAction.tile == tile) results.add(tileAction);
            }
        }
        return results;
    }

    public Array<Action> getActions(Player actor) {
        Array<Action> results = new Array<>();
        for (Action action : actions) {
            if (action.actor == actor) results.add(action);
        }
        return results;
    }

    public void reset() {
        actions.clear();
    }
}
