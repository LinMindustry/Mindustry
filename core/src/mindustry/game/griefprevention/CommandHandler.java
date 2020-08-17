package mindustry.game.griefprevention;

import arc.Core;
import arc.math.geom.Vec2;
import arc.struct.Array;
import arc.func.Cons;
import arc.util.Log;
import mindustry.entities.type.Player;
import mindustry.game.griefprevention.Actions.Action;
import mindustry.game.griefprevention.Actions.TileAction;
import mindustry.game.griefprevention.Actions.UndoResult;
import mindustry.gen.Call;
import mindustry.net.Packets.AdminAction;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.BlockPart;
import org.mozilla.javascript.*;

import static mindustry.Vars.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

// introducing the worst command system known to mankind
public class CommandHandler {
    public static class CommandContext {
        public List<String> args;

        public CommandContext(List<String> args) {
            this.args = args;
        }
    }

    public ContextFactory scriptContextFactory = new ContextFactory();
    public Context scriptContext;
    public Scriptable scriptScope;
    public HashMap<String, Cons<CommandContext>> commands = new HashMap<>();

    public CommandHandler() {
        addCommand("fixpower", this::fixPower);
        addCommand("verbose", settingsToggle("verbose", "verbose logging", v -> griefWarnings.verbose = v));
        addCommand("debug", settingsToggle("debug", "debug logging", v -> griefWarnings.debug = v));
        addCommand("spam", settingsToggle("spam", "verbose and debug logging", v -> {
            griefWarnings.verbose = v;
            griefWarnings.debug = v;
        }));
        addCommand("broadcast", settingsToggle("broadcast", "broadcast of messages", v -> griefWarnings.broadcast = v));
        addCommand("tileinfo", this::tileInfo);
        addCommand("players", this::players);
        addCommand("votekick", this::votekick);
        addCommand("tileinfohud", settingsToggle("tileinfohud", "tile information hud", v -> griefWarnings.tileInfoHud = v));
        addCommand("autoban", settingsToggle("autoban", "automatic bans", v -> griefWarnings.autoban = v));
        addCommand("autotrace", settingsToggle("autotrace", "automatic trace", v -> griefWarnings.autotrace = v));
        addCommand("auto", this::auto);
        addCommand("nextwave", this::nextwave);
        addCommand("playerinfo", this::playerInfo);
        addCommand("pi", this::playerInfo); // playerinfo takes too long to type
        addCommand("eval", this::eval);
        addCommand("freecam", createToggle("freecam", "free movement of camera", v -> griefWarnings.auto.setFreecam(v)));
        addCommand("show", this::show);
        addCommand("logactions", settingsToggle("logactions", "log all actions captured by the action log", v -> griefWarnings.logActions = v));
        addCommand("getactions", this::getactions);
        addCommand("undoactions", this::undoactions);

        // mods context not yet initialized here
        scriptContext = scriptContextFactory.enterContext();
        scriptContext.setOptimizationLevel(9);
        scriptContext.getWrapFactory().setJavaPrimitiveWrap(false);
        scriptScope = new ImporterTopLevel(scriptContext);

        try {
            scriptContext.evaluateString(scriptScope, Core.files.internal("scripts/global.js").readString(), "global.js", 1, null);
        } catch (Throwable ex) {
            Log.err("global.js load failed", ex);
        } finally {
            Context.exit();
        }
    }

    public String runConsole(String text) {
        Context prevContext = Context.getCurrentContext();
        if (prevContext != null) Context.exit();
        Context ctx = scriptContextFactory.enterContext(scriptContext);
        try {
            Object o = ctx.evaluateString(scriptScope, text, "console.js", 1, null);
            if(o instanceof NativeJavaObject){
                o = ((NativeJavaObject)o).unwrap();
            }
            if(o instanceof Undefined){
                o = "undefined";
            }
            return String.valueOf(o);
        } catch(Throwable t) {
            Log.err("Script error", t);
            return t.toString();
        } finally {
            Context.exit();
            if (prevContext != null) platform.enterScriptContext(prevContext);
        }
    }

    public void addCommand(String name, Cons<CommandContext> handler) {
        commands.put(name, handler);
    }

    public void reply(String message) {
        ui.chatfrag.addMessage(message, null);
    }

    public boolean runCommand(String message) {
        if (!message.startsWith("/")) return false;
        String[] args = message.split(" ");
        args[0] = args[0].substring(1);
        Cons<CommandContext> command = commands.get(args[0].toLowerCase());
        if (command == null) return false;
        command.get(new CommandContext(Arrays.asList(args)));
        return true;
    }

    /**
     * Reconnect every power node to everything it can connect to, intended to
     * be used after power griefing incidents.
     * If "redundant" is present as an argument, connect the block even if it is
     * already part of the same power graph.
     */
    public void fixPower(CommandContext ctx) {
        boolean redundant = ctx.args.contains("redundant");
        griefWarnings.fixer.fixPower(redundant);
        reply("[green]Done");
    }

    public Cons<CommandContext> createToggle(String name, String description, Cons<Boolean> consumer) {
        return ctx -> {
            if (ctx.args.size() < 2) {
                reply("[scarlet]Not enough arguments");
                reply("Usage: " + name + " <on|off>");
                return;
            }
            switch (ctx.args.get(1).toLowerCase()) {
                case "on":
                case "true":
                    consumer.get(true);
                    reply("Enabled " + description);
                    break;
                case "off":
                case "false":
                    consumer.get(false);
                    reply("Disabled " + description);
                    break;
                default:
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: " + name + " <on|off>");
            }
        };
    }

    public Cons<CommandContext> settingsToggle(String name, String description, Cons<Boolean> consumer) {
        return createToggle(name, description, v -> {
            consumer.get(v);
            griefWarnings.saveSettings();
        });
    }

    public Array<String> tileInfo(Tile tile) {
        TileInfo info = griefWarnings.tileInfo.get(tile);
        Array<String> out = new Array<>();
        out.add("Tile at " + griefWarnings.formatTile(tile));
        Block currentBlock = tile.block();
        if (currentBlock == null) {
            out.add("[yellow]Nonexistent block");
            return out;
        }
        if (currentBlock instanceof BlockPart) currentBlock = tile.link().block();
        out.add("Current block: " + currentBlock.name);
        out.add("Team: [#" + tile.getTeam().color + "]" + tile.getTeam() + "[]");
        if (info == null) {
            out.add("[yellow]No information");
            return out;
        }
        Block previousBlock = info.previousBlock;
        Player deconstructedBy = info.deconstructedBy;
        if (info.link != null) info = info.link;
        out.add("Constructed by: " + griefWarnings.formatPlayer(info.constructedBy));
        out.add("Deconstructed by: " + griefWarnings.formatPlayer(deconstructedBy));
        if (previousBlock != null) out.add("Block that was here: " + previousBlock.name);
        out.add("Configured [accent]" + info.configureCount + "[] times");
        if (info.interactedPlayers.size > 0) {
            out.add("Players who have interacted with this block:");
            for (Player player : info.interactedPlayers.iterator()) {
                out.add("  - " + griefWarnings.formatPlayer(player));
            }
        } else out.add("No interaction information recorded");
        if (info.lastInteractedBy != null) out.add("Last interacted by: " + griefWarnings.formatPlayer(info.lastInteractedBy));
        if (info.lastRotatedBy != null) out.add("Last rotated by: " + griefWarnings.formatPlayer(info.lastRotatedBy));
        return out;
    }

    /** Get stored information for the tile under the cursor */
    public void tileInfo(CommandContext ctx) {
        Tile tile = getCursorTile();
        if (tile == null) {
            reply("cursor is not on a tile");
            return;
        }
        Array<String> out = tileInfo(tile);
        if (ctx.args.contains("send")) {
            for (String line : out) griefWarnings.sendMessage(line, false);
        } else {
            reply("====================");
            reply(String.join("\n", out));
        }
    }

    public Tile getCursorTile() {
        Vec2 vec = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY());
        return world.tile(world.toTile(vec.x), world.toTile(vec.y));
    }

    /** Get list of all players and their ids */
    public void players(CommandContext ctx) {
        reply("Players:");
        for (Player target : playerGroup.all()) {
            StringBuilder response = new StringBuilder();
            response.append("[accent]*[] ")
                    .append(griefWarnings.formatPlayer(target))
                    .append(" raw: ")
                    .append(target.name.replaceAll("\\[", "[["));
            PlayerStats stats = griefWarnings.playerStats.get(target);
            if (stats != null && stats.trace != null) {
                response.append(" trace: ")
                        .append(griefWarnings.formatTrace(stats.trace));
            }
            reply(response.toString());
        }
    }

    /** Get information about a player */
    public void playerInfo(CommandContext ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        PlayerStats stats = getStats(name);
        if (stats == null) {
            reply("[scarlet]Not found");
            return;
        }
        Player target = stats.wrappedPlayer.get();
        if (target == null) {
            reply("[scarlet]PlayerStats weakref gone?");
            return;
        }
        Core.app.setClipboardText(Integer.toString(target.id));
        String r = "====================\n" +
                "Player " + griefWarnings.formatPlayer(target) + "\n" +
                "gone: " + stats.gone + "\n" +
                "position: (" + target.getX() + ", " + target.getY() + ")\n" +
                "trace: " + griefWarnings.formatTrace(stats.trace) + "\n" +
                "blocks constructed: " + stats.blocksConstructed + "\n" +
                "blocks broken: " + stats.blocksBroken + "\n" +
                "configure count: " + stats.configureCount + "\n" +
                "rotate count: " + stats.rotateCount + "\n" +
                "configure ratelimit: " + griefWarnings.formatRatelimit(stats.configureRatelimit) + "\n" +
                "rotate ratelimit: " + griefWarnings.formatRatelimit(stats.rotateRatelimit) + "\n" +
                "Player id copied to clipboard";
        reply(r);
    }

    /** Get player by either id or full name */
    public Player getPlayer(String name) {
        Player target;
        if (name.startsWith("&")) {
            int ref;
            try {
                ref = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ex) {
                ref = -1;
            }
            target = griefWarnings.refs.get(ref);
        } else if (name.startsWith("#")) {
            int id;
            try {
                id = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ex) {
                id = -1;
            }
            target = playerGroup.getByID(id);
        } else {
            target = playerGroup.find(p -> p.name.equalsIgnoreCase(name));
        }
        return target;
    }

    public Tile findTile(String a, String b) {
        int x;
        int y;
        try {
            x = Integer.parseInt(a);
            y = Integer.parseInt(b);
        } catch (NumberFormatException ex) {
            return null;
        }
        return world.tile(x, y);
    }

    /** Get information on player, including historical data */
    public PlayerStats getStats(String name) {
        if (name.startsWith("&")) {
            int ref;
            try {
                ref = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ex) {
                return null;
            }
            Player target = griefWarnings.refs.get(ref);
            if (target.stats != null) return target.stats;
            else return griefWarnings.getOrCreatePlayerStats(target);
        } else if (name.startsWith("#")) {
            int id;
            try {
                id = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException ex) {
                return null;
            }
            for (Entry<Player, PlayerStats> e : griefWarnings.playerStats.entrySet()) {
                if (e.getKey().id == id) return e.getValue();
            }
        } else {
            for (Entry<Player, PlayerStats> e : griefWarnings.playerStats.entrySet()) {
                if (e.getKey().name.equalsIgnoreCase(name)) return e.getValue();
            }
        }
        return null;
    }

    /** Votekick overlay to allow /votekick using ids when prefixed by # */
    public void votekick(CommandContext ctx) {
        String name = String.join(" ", ctx.args.subList(1, ctx.args.size())).toLowerCase();
        Player target = getPlayer(name);
        if (target == null) {
            reply("[scarlet]Player not found!");
            return;
        }
        reply("[cyan]Votekicking player:[] " + griefWarnings.formatPlayer(target));
        Call.sendChatMessage("/votekick " + target.name);
    }

    /** Control the auto mode */
    public void auto(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("[scarlet]Not enough arguments");
            reply("Usage: auto <on|off|cancel|gotocore|gotoplayer|goto|distance|itemsource|dumptarget|pickuptarget>");
            return;
        }
        Auto auto = griefWarnings.auto;
        switch (ctx.args.get(1).toLowerCase()) {
            case "on":
                auto.enabled = true;
                reply("enabled auto mode");
                break;
            case "off":
                auto.enabled = false;
                reply("disabled auto mode");
                break;
            case "gotocore":
                Tile core = player.getClosestCore().getTile();
                auto.gotoTile(core, 50f);
                reply("going to tile " + griefWarnings.formatTile(core));
                break;
            case "goto": {
                if (ctx.args.size() < 4) {
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: auto goto [persist] <x> <y>");
                    return;
                }
                int argStart = 2;
                boolean persist = false;
                String additional = ctx.args.get(argStart).toLowerCase();
                if (additional.equals("persist")) {
                    argStart++;
                    persist = true;
                }
                Tile tile = findTile(ctx.args.get(argStart), ctx.args.get(argStart + 1));
                if (tile == null) {
                    reply("[scarlet]Invalid tile");
                    return;
                }
                auto.gotoTile(tile, persist ? 0f : 50f);
                auto.persist = persist;
                reply("going to tile " + griefWarnings.formatTile(tile));
                break;
            }
            case "gotoplayer": {
                if (ctx.args.size() < 3) {
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: auto gotoplayer [follow|assist|undo] <player>");
                    return;
                }
                int nameStart = 2;
                boolean follow = false;
                boolean assist = false;
                boolean undo = false;
                float distance = 100f;
                String additional = ctx.args.get(nameStart).toLowerCase();
                switch (additional) {
                    case "follow":
                        nameStart++;
                        follow = true;
                        break;
                    case "assist":
                        nameStart++;
                        assist = true;
                        distance = 50f;
                        break;
                    case "undo":
                        nameStart++;
                        undo = true;
                        distance = 50f;
                        break;
                }
                String name = String.join(" ", ctx.args.subList(nameStart, ctx.args.size()));
                Player target = getPlayer(name);
                if (target == null) {
                    reply("[scarlet]No such player");
                    return;
                }
                if (assist) auto.assistEntity(target, distance);
                else if (undo) auto.undoEntity(target, distance);
                else auto.gotoEntity(target, distance, follow);
                reply("going to player: " + griefWarnings.formatPlayer(target));
                break;
            }
            case "cancel": {
                auto.cancelMovement();
                reply("cancelled");
                break;
            }
            case "distance": {
                if (ctx.args.size() < 3) {
                    reply("[scarlet]Not enough arguments");
                    reply("Usage: auto distance <distance>");
                    return;
                }
                float distance;
                try {
                    distance = Float.parseFloat(ctx.args.get(2));
                } catch (NumberFormatException ex) {
                    reply("[scarlet]Invalid number");
                    return;
                }
                auto.targetDistance = distance;
                reply("set target distance to " + distance);
                break;
            }
            case "itemsource": {
                if (ctx.args.size() == 3) {
                    if (ctx.args.get(2).toLowerCase().equals("cancel")) {
                        auto.manageItemSource(null);
                        reply("cancelled automatic item source configuration");
                        return;
                    }
                }
                Tile tile = getCursorTile();
                if (tile == null) {
                    reply("cursor is not on a tile");
                    return;
                }
                if (!auto.manageItemSource(tile)) {
                    reply("target tile is not an item source");
                    return;
                }
                reply("automatically configuring item source " + griefWarnings.formatTile(tile));
                break;
            }
            case "dumptarget": {
                // usage: /auto dumptarget [<x> <y>]
                Tile tile = null;
                if (ctx.args.size() == 3) {
                    if (ctx.args.get(2).toLowerCase().equals("reset")) {
                        auto.setAutoDumpTransferTarget(null);
                        reply("reset autodump target");
                        return;
                    }
                } else if (ctx.args.size() == 4) {
                    tile = findTile(ctx.args.get(2), ctx.args.get(3));
                } else tile = getCursorTile();
                if (tile == null) {
                    reply("cursor is not on a tile or invalid tile specified");
                    return;
                }
                if (tile.isLinked()) tile = tile.link();
                if (!auto.setAutoDumpTransferTarget(tile)) {
                    reply("target does not seem valid");
                    return;
                }
                reply("automatically dumping player inventory to tile " + griefWarnings.formatTile(tile));
                break;
            }
            case "pickuptarget": {
                // usage: /auto pickuptarget [<x> <y>] <item>
                Item item;
                Tile tile;
                if (ctx.args.size() == 3) {
                    if (ctx.args.get(2).toLowerCase().equals("reset")) {
                        auto.setAutoPickupTarget(null, null);
                        reply("reset autopickup target");
                        return;
                    }

                    item = content.items().find(a -> a.name.equals(ctx.args.get(2)));
                    tile = getCursorTile();
                } else if (ctx.args.size() == 5) {
                    item = content.items().find(a -> a.name.equals(ctx.args.get(4)));
                    tile = findTile(ctx.args.get(2), ctx.args.get(3));
                } else {
                    reply("invalid arguments");
                    return;
                }
                if (item == null) {
                    reply("invalid item provided");
                    return;
                }
                if (tile == null) {
                    reply("cursor is not on a tile");
                    return;
                }
                if (tile.isLinked()) tile = tile.link();
                if (!auto.setAutoPickupTarget(tile, item)) {
                    reply("target does not seem valid");
                    return;
                }
                reply("automatically picking up item " + item.name + " from tile " + griefWarnings.formatTile(tile));
                break;
            }
            default:
                reply("unknown subcommand");
        }
    }

    public void nextwave(CommandContext ctx) {
        if (!player.isAdmin) {
            reply("not admin!");
            return;
        }
        int count = 1;
        if (ctx.args.size() > 1) {
            try {
                count = Integer.parseInt(ctx.args.get(1));
            } catch (NumberFormatException ex) {
                reply("invalid number");
                return;
            }
        }
        for (int i = 0; i < count; i++) Call.onAdminRequest(player, AdminAction.wave);
        reply("done");
    }

    public void eval(CommandContext ctx) {
        String code = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        reply(runConsole(code));
    }

    /** Switch to freecam and focus on an object */
    public void show(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("No target given");
            return;
        }

        if (ctx.args.size() == 3) {
            Tile tile = findTile(ctx.args.get(1), ctx.args.get(2));
            if (tile != null) {
                reply("Showing tile " + griefWarnings.formatTile(tile));
                griefWarnings.auto.setFreecam(true, tile.getX(), tile.getY());
                return;
            }
        }

        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        Player target = getPlayer(name);
        if (target == null) {
            reply("Target does not exist");
            return;
        }

        reply("Showing player " + griefWarnings.formatPlayer(target));
        griefWarnings.auto.setFreecam(true, target.x, target.y);
    }

    /** Show action logs relevant to tile or player */
    public void getactions(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("No target given");
            return;
        }

        if (ctx.args.size() == 3) {
            Tile tile = findTile(ctx.args.get(1), ctx.args.get(2));
            if (tile != null) {
                reply("Showing actions for tile " + griefWarnings.formatTile(tile));
                Array<TileAction> actions = griefWarnings.actionLog.getActions(tile);
                // print backwards
                for (int i = actions.size - 1; i >= 0; i--) {
                    reply(actions.get(i).toString());
                }
                return;
            }
        }

        String name = String.join(" ", ctx.args.subList(1, ctx.args.size()));
        Player target = getPlayer(name);
        if (target == null) {
            reply("Target does not exist");
            return;
        }

        Array<Action> actions = griefWarnings.actionLog.getActions(target);
        for (int i = actions.size - 1; i >= 0; i--) {
            reply(actions.get(i).toString());
        }
    }

    /** Undo actions of player */
    public void undoactions(CommandContext ctx) {
        if (ctx.args.size() < 2) {
            reply("No target given");
            return;
        }

        int count = -1;
        if (ctx.args.size() > 2) {
            try {
                count = Integer.parseInt(ctx.args.get(1));
            } catch (NumberFormatException ex) {
                // ignore
            }
        }

        int argStart = count > -1 ? 2 : 1;
        String name = String.join(" ", ctx.args.subList(argStart, ctx.args.size()));
        Player target = getPlayer(name);
        if (target == null) {
            reply("Invalid target");
            return;
        }

        Array<Action> actions = griefWarnings.actionLog.getActions(target);
        int j = 0;
        for (Action action : actions) {
            reply("[green]Undo:[] " + action.toString());
            if (action.undo() == UndoResult.mismatch) reply("[scarlet]mismatch");
            if (count > 0 && ++j >= count) break;
        }
    }
}
