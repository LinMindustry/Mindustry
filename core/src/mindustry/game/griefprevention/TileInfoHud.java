package mindustry.game.griefprevention;

import arc.Core;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import mindustry.gen.Tex;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class TileInfoHud extends Table {
    private Tile lastTile = null;
    private String lastOutput = "No data";

    public TileInfoHud() {
        touchable(Touchable.disabled);
        background(Tex.pane);
        label(this::hudInfo);
    }

    public String hudInfo() {
        Tile tile = griefWarnings.commandHandler.getCursorTile();
        if (tile == lastTile) return lastOutput;
        if (tile == null) return lastOutput = "No data";
        return lastOutput = String.join("\n", griefWarnings.commandHandler.tileInfo(tile));
    }
}
