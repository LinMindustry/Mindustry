package mindustry.game.griefprevention;

import arc.struct.Array;
import arc.struct.ObjectSet;
import arc.func.Boolf;
import arc.func.Cons;
import arc.math.geom.Geometry;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.power.PowerNode;

import java.lang.reflect.Field;

import static mindustry.Vars.*;

public class FixGrief {
    // need this to get powernode laser range
    public Class<PowerNode> powerNodeClass = PowerNode.class;
    public Field powerNodeLaserLengthField;

    public FixGrief() {
        // set up reflective access of laser range
        try {
            powerNodeLaserLengthField = powerNodeClass.getDeclaredField("laserRange");
            powerNodeLaserLengthField.setAccessible(true);
        } catch (Exception ex) {
            throw new RuntimeException("Unable to set up reflective access to PowerNode laserRange field", ex);
        }
    }

    public void iterateAllTiles(Cons<Tile> fn) {
        Tile[][] tiles = world.getTiles();
        for (int x = 0; x < tiles.length; x++) {
            for (int y = 0; y < tiles[x].length; y++) {
                fn.get(tiles[x][y]);
            }
        }
    }

    // copied from PowerNode.java
    public void getPotentialPowerLinks(Tile tile, boolean redundant, Cons<Tile> others) {
        PowerNode block = (PowerNode)tile.block();
        float laserRange;
        try {
            laserRange = (float)powerNodeLaserLengthField.get(block);
        } catch (Exception ex) {
            // this should never fail
            throw new RuntimeException("Failed to access PowerNode laserLength field");
        }
        ObjectSet<PowerGraph> graphs = new ObjectSet<>();
        Array<Tile> tempTiles = new Array<>();

        Boolf<Tile> valid = other -> other != null && other != tile && other.entity != null && other.entity.power != null &&
        ((!other.block().outputsPower && other.block().consumesPower) || (other.block().outputsPower && !other.block().consumesPower) || other.block() instanceof PowerNode) &&
        block.overlaps(tile.x * tilesize + block.offset(), tile.y * tilesize + block.offset(), other, laserRange * tilesize) && other.getTeam() == player.getTeam()
        && !other.entity.proximity().contains(tile) && !graphs.contains(other.entity.power.graph);

        Geometry.circle(tile.x, tile.y, (int)(laserRange + 1), (x, y) -> {
            Tile other = world.ltile(x, y);
            if(valid.get(other) && !tempTiles.contains(other)){
                tempTiles.add(other);
            }
        });

        tempTiles.sort((a, b) -> {
            int type = -Boolean.compare(a.block() instanceof PowerNode, b.block() instanceof PowerNode);
            if(type != 0) return type;
            return Float.compare(a.dst2(tile), b.dst2(tile));
        });
        tempTiles.each(valid, t -> {
            if (!redundant) graphs.add(t.entity.power.graph);
            others.get(t);
        });
    }

    // will massively attempt to reconnect every power node to every block in range that accepts power,
    // including other power nodes
    public void fixPower(boolean redundant) {
        iterateAllTiles(tile -> {
            if (!(tile.block() instanceof PowerNode)) return;
            if (tile.getTeam() != player.getTeam()) return;
            getPotentialPowerLinks(tile, redundant, link -> {
                int value = link.pos();
                boolean contains = tile.entity.power.links.contains(value);
                if (!contains) tile.configure(value);
            });
        });
    }
}
