package mindustry.game.griefprevention;

import arc.math.Mathf;
import mindustry.content.Blocks;
import mindustry.entities.traits.BuilderTrait.BuildRequest;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Pos;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerNode;

import java.util.Date;

import static mindustry.Vars.*;

public class Actions {
    public enum UndoResult {
        success, mismatch, unavailable
    }

    public static abstract class Action {
        public static String name;
        public Player actor;
        public Date timestamp;

        public Action(Player actor) {
            this.actor = actor;
            this.timestamp = new Date();
        }

        public UndoResult undo() {
            return UndoResult.unavailable;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static abstract class TileAction extends Action {
        public Tile tile;

        public TileAction(Player actor, Tile tile) {
            super(actor);
            this.tile = tile;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class Construct extends TileAction {
        public static String name = "Construct";

        public Block previousBlock;
        public int previousRotation;
        public int previousConfig;
        public Block constructBlock;
        public int constructRotation;

        public Construct(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != constructBlock) return UndoResult.mismatch;
            if (previousBlock == null || !previousBlock.canReplace(tile.block())) { // don't deconstruct if replacement possible
                BuildRequest removeRequest = new BuildRequest(tile.x, tile.y);
                player.buildQueue().addLast(removeRequest);
            }
            if (previousBlock != null && previousBlock.isVisible()) {
                BuildRequest rebuildRequest = new BuildRequest(tile.x, tile.y, previousRotation, previousBlock);
                rebuildRequest.configure(previousConfig);
                player.buildQueue().addLast(rebuildRequest);
            }
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "previousBlock: " + (previousBlock != null ? previousBlock.name : "null") + ", " +
                    "previousRotation: " + previousRotation + ", " +
                    "previousConfig: " + previousConfig + ", " +
                    "constructBlock: " + constructBlock.name + ", " +
                    "constructRotation: " + constructRotation + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class Deconstruct extends TileAction {
        public static String name = "Deconstruct";

        public Block previousBlock;
        public int previousRotation;
        public int previousConfig;

        public Deconstruct(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            // if there's already a build request for this location, don't shove more on the build queue
            // NOTE: this is potentially expensive?
            if (player.buildQueue().indexOf(b -> b.x == tile.x && b.y == tile.y && !b.breaking) > -1) {
                return UndoResult.mismatch;
            }
            if (previousBlock.isVisible()) {
                BuildRequest rebuildRequest = new BuildRequest(tile.x, tile.y, previousRotation, previousBlock);
                rebuildRequest.configure(previousConfig);
                player.buildQueue().addLast(rebuildRequest);
            }
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "previousBlock: " + previousBlock.name + ", " +
                    "previousRotation: " + previousRotation + ", " +
                    "previousConfig: " + previousConfig + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    // used for tiles with positional configuration (mass drivers, item bridges, etc)
    public static class ConfigurePositional extends TileAction {
        public static String name = "ConfigurePositional";

        public Block targetBlock;
        public int beforeConfig;
        public int afterConfig;

        public ConfigurePositional(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != targetBlock) return UndoResult.mismatch;
            if (tile.entity == null || tile.entity.config() != afterConfig) return UndoResult.mismatch;
            tile.configure(beforeConfig);
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "targetBlock: " + targetBlock.name + ", " +
                    "beforeConfig: " + beforeConfig + ", " +
                    "afterConfig: " + afterConfig + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    // used for tiles with item/liquid selection configuration (unloaders, sorters, item source, etc)
    public static class ConfigureItemSelect extends TileAction {
        public static String name = "ConfigureItemSelect";

        public Block targetBlock;
        public int beforeConfig;
        public int afterConfig;

        public ConfigureItemSelect(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != targetBlock) return UndoResult.mismatch;
            if (tile.entity == null || tile.entity.config() != afterConfig) return UndoResult.mismatch;
            tile.configure(beforeConfig);
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "targetBlock: " + targetBlock.name + ", " +
                    "beforeConfig: " + beforeConfig + ", " +
                    "afterConfig: " + afterConfig + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class ConfigurePowerNode extends TileAction {
        public static String name = "ConfigurePowerNode";

        // whether the configure was a disconnect
        public boolean disconnect;
        public int other;

        public ConfigurePowerNode(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (!(tile.block() instanceof PowerNode)) return UndoResult.mismatch;
            boolean has = tile.entity.power.links.contains(other);;
            if (disconnect == has) return UndoResult.mismatch;
            tile.configure(other);
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "disconnect: " + disconnect + ", " +
                    "other: (" + Pos.x(other) + ", " + Pos.y(other) + "), " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class DepositItems extends TileAction {
        public static String name = "DepositItems";

        public Item item;
        public int amount;

        public DepositItems(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "item: " + item.name + ", " +
                    "amount: " + amount + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class WithdrawItems extends TileAction {
        public static String name = "WithdrawItems";

        public Item item;
        public int amount;

        public WithdrawItems(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "item: " + item.name + ", " +
                    "amount: " + amount + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class RotateBlock extends TileAction {
        public static String name = "RotateBlock";

        public Block targetBlock;
        public int beforeRotation;
        public boolean direction;

        public RotateBlock(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public UndoResult undo() {
            if (tile.block() != targetBlock) return UndoResult.mismatch;
            if (tile.rotation() != Mathf.mod(beforeRotation + Mathf.sign(direction), 4)) {
                return UndoResult.mismatch;
            }
            Call.rotateBlock(player, tile, !direction);
            return UndoResult.success;
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "tile: " + griefWarnings.formatTile(tile) + ", " +
                    "targetBlock: " + targetBlock.name + ", " +
                    "beforeRotation: " + beforeRotation + ", " +
                    "direction: " + (direction ? 1 : -1) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }

    public static class TapTile extends TileAction {
        public static String name = "TapTile";

        public TapTile(Player actor, Tile tile) {
            super(actor, tile);
        }

        @Override
        public String toString() {
            return name + " { " +
                    "actor: " + griefWarnings.formatPlayer(actor) + ", " +
                    "timestamp: " + timestamp.toString() + " }";
        }
    }
}
