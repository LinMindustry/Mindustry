package mindustry.game.griefprevention;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Array;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.type.Player;
import mindustry.entities.type.Unit;
import mindustry.game.EventType.DepositEvent;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.TileChangeEvent;
import mindustry.game.EventType.WithdrawEvent;
import mindustry.gen.Call;
import mindustry.net.Administration.TraceInfo;
import mindustry.net.Packets.AdminAction;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.power.ItemLiquidGenerator;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.sandbox.ItemSource;
import mindustry.world.blocks.sandbox.LiquidSource;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.blocks.storage.Unloader;
import mindustry.world.blocks.storage.Vault;

import org.json.JSONObject;

import static mindustry.Vars.*;
import static mindustry.Vars.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.WeakHashMap;

public class GriefWarnings {
    private Instant nextWarningTime = Instant.now();
    public WeakHashMap<Tile, TileInfo> tileInfo = new WeakHashMap<>();
    public WeakHashMap<Player, PlayerStats> playerStats = new WeakHashMap<>();
    /** whether or not to send warnings to all players */
    public boolean broadcast = false;
    /** whether or not to be very noisy about everything */
    public boolean verbose = false;
    /** whether or not to flat out state the obvious, pissing everyone off */
    public boolean debug = false;
    /** whether or not to show the persistent tileinfo display */
    public boolean tileInfoHud = true;
    /** whether or not to automatically ban when we are 100% sure that player is griefing (eg. intentionally crashing other clients) */
    public boolean autoban = false;
    /** whether to automatically perform an admin trace on player joins */
    public boolean autotrace = true;
    /** whether to log every action captured by the action log */
    public boolean logActions = false;

    public CommandHandler commandHandler = new CommandHandler();
    public FixGrief fixer = new FixGrief();
    public Auto auto;
    public RefList refs = new RefList();
    public ActionLog actionLog = new ActionLog();

    public GriefWarnings() {
        Events.on(DepositEvent.class, this::handleDeposit);
        Events.on(WithdrawEvent.class, this::handleWithdraw);
        Events.on(TileChangeEvent.class, this::handleTileChange);
        Events.on(ResetEvent.class, this::reset);

        loadSettings();
    }

    public void loadSettings() {
        broadcast = Core.settings.getBool("griefwarnings.broadcast", false);
        verbose = Core.settings.getBool("griefwarnings.verbose", false);
        debug = Core.settings.getBool("griefwarnings.debug", false);
        tileInfoHud = Core.settings.getBool("griefwarnings.tileinfohud", true);
        autoban = Core.settings.getBool("griefwarnings.autoban", false);
        autotrace = Core.settings.getBool("griefwarnings.autotrace", true);
        logActions = Core.settings.getBool("griefwarnings.logactions", false);
    }

    public void saveSettings() {
        Core.settings.put("griefwarnings.broadcast", broadcast);
        Core.settings.put("griefwarnings.verbose", verbose);
        Core.settings.put("griefwarnings.debug", debug);
        Core.settings.put("griefwarnings.tileinfohud", tileInfoHud);
        Core.settings.put("griefwarnings.autoban", autoban);
        Core.settings.put("griefwarnings.autotrace", autotrace);
        Core.settings.put("griefwarnings.logactions", logActions);
        Core.settings.save();
    }

    public boolean sendMessage(String message, boolean throttled) {
        // if (!net.active()) return false;
        if (message.length() > maxTextLength) {
            ui.chatfrag.addMessage(
                    "[scarlet]WARNING: the following grief warning exceeded maximum allowed chat length and was not sent",
                    null);
            ui.chatfrag.addMessage(message, null);
            ui.chatfrag.addMessage("Message length was [accent]" + message.length(), null);
            Log.warn("[antigrief] Oversize message not sent (size " + message.length() + "): " + message);
            return false;
        }
        if (!Instant.now().isAfter(nextWarningTime) && throttled) return false;
        nextWarningTime = Instant.now().plusSeconds(1);
        if (broadcast) Call.sendChatMessage(message);
        else if (net.client()) ui.chatfrag.addMessage(message, null);
        else if (net.server()) Log.info("[antigrief] " + message);
        return true;
    }

    public boolean sendMessage(String message) {
        return sendMessage(message, true);
    }

    public void sendLocal(String message) {
        ui.chatfrag.addMessage(message, null);
    }

    public float getDistanceToCore(Unit unit, float x, float y) {
        Tile nearestCore = unit.getClosestCore().getTile();
        return Mathf.dst(x, y, nearestCore.x, nearestCore.y);
    }

    public float getDistanceToCore(Unit unit, Tile tile) {
        return getDistanceToCore(unit, tile.x, tile.y);
    }

    public float getDistanceToCore(Unit unit) {
        return getDistanceToCore(unit, unit.x, unit.y);
    }

    public void reset() {
        tileInfo.clear();
        playerStats.clear();
        refs.reset();
        actionLog.reset();
        if (auto != null) auto.reset();
    }

    public void handleTileChange(TileChangeEvent event) {
        // if (event.tile.block() == Blocks.air) tileInfo.remove(event.tile);
    }

    public TileInfo getOrCreateTileInfo(Tile tile, boolean doLinking) {
        TileInfo info = tile.info;
        if (info == null) {
            TileInfo newInfo = new TileInfo();
            info = newInfo;
            tileInfo.put(tile, newInfo);
            tile.info = newInfo;
            if (doLinking) tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).doLink(newInfo));
        }
        return info;
    }

    public TileInfo getOrCreateTileInfo(Tile tile) {
        return getOrCreateTileInfo(tile, true);
    }

    public PlayerStats getOrCreatePlayerStats(Player target) {
        PlayerStats stats = target.stats;
        if (stats == null) {
            stats = new PlayerStats(target);
            playerStats.put(target, stats);
            target.stats = stats;
            refs.get(target); // create ref
            if (target == player) return stats;
            if (player.isAdmin && autotrace) {
                stats.doTrace(trace -> {
                    // custom
                    // sendLocal("[accent]Player join:[] " + formatPlayer(target) + " " + formatTrace(trace));
                    Log.infoTag("antigrief", "Player join: " + target.name + " (" + player.id+ ") " + formatTrace(trace));
                });
            } else {
                // custom
                // sendLocal("[accent]Player join:[] " + formatPlayer(target));
                // acquire trace info
                Call.onAdminRequest(target, AdminAction.trace);
                // request info
                // Call.sendChatMessage("/info " + player.uuid);
            }
        }
        return stats;
    }

    // these are called before the BuildBlock is placed so tile contains the previous block
    public void handleBeginBreak(Tile tile) {
        TileInfo info = getOrCreateTileInfo(tile);
        info.previousBlock = tile.block();
        info.previousRotation = tile.rotation();
        info.previousConfig = (tile.entity != null) ? tile.entity.config() : -1;
    }

    public void handleBeginPlace(Tile tile, Block result, int rotation) {
        TileInfo info = getOrCreateTileInfo(tile);
        info.previousBlock = tile.block();
        info.previousRotation = tile.rotation();
        info.previousConfig = (tile.entity != null) ? tile.entity.config() : -1;
    }

    public void handleBlockConstructProgress(Player builder, Tile tile, Block cblock, float progress, Block previous) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (builder != null) info.constructedBy = builder;

        boolean didWarn = false;
        float coreDistance = getDistanceToCore(builder, tile);
        // persistent warnings that keep showing
        if (coreDistance < 30 && cblock instanceof NuclearReactor && !mobile) {
            String message = "[scarlet]WARNING[] " + formatPlayer(builder) + " is building a reactor [stat]" +
                    Math.round(coreDistance) + "[] blocks from core. [stat]" + Math.round(progress * 100) + "%";
            sendMessage(message);
            didWarn = true;
        } else if (coreDistance < 10 && cblock instanceof ItemLiquidGenerator && !mobile) {
            String message = "[scarlet]WARNING[] " + formatPlayer(builder) + " is building a generator [stat]" +
                    Math.round(coreDistance) + "[] blocks from core. [stat]" + Math.round(progress * 100) + "%";
            sendMessage(message);
            didWarn = true;
        }
        

        // one-time block construction warnings
        if (!info.constructSeen) {
            tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).doLink(info));
            info.constructSeen = true;
            info.currentBlock = cblock;

            Actions.Construct action = new Actions.Construct(builder, tile);
            action.previousBlock = info.previousBlock;
            action.previousRotation = info.previousRotation;
            action.previousConfig = info.previousConfig;
            action.constructBlock = cblock;
            action.constructRotation = tile.rotation();
            actionLog.add(action);

            if (!didWarn) {
                if (cblock instanceof NuclearReactor) {
                    Array<Tile> bordering = tile.entity.proximity;
                    boolean hasCryo = false;
                    for (Tile neighbor : bordering) {
                        if (
                            neighbor.entity != null && neighbor.entity.liquids != null &&
                            neighbor.entity.liquids.current() == Liquids.cryofluid
                        ) {
                            hasCryo = true;
                            break;
                        }
                    }
                    if (!hasCryo) {
                        String message = "[lightgray]Notice[] " + formatPlayer(builder) + 
                            " is building a reactor at " + formatTile(tile);
                        sendMessage(message, false);
                    }
                }
            }
        }
    }

    public void handleBlockConstructFinish(Tile tile, Block block, int builderId) {
        TileInfo info = getOrCreateTileInfo(tile);
        Player targetPlayer = playerGroup.getByID(builderId);
        tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).doLink(info));
        info.constructedBy = targetPlayer;
        info.currentBlock = block;

        if (targetPlayer != null) {
            if (!info.constructSeen) {
                Actions.Construct action = new Actions.Construct(targetPlayer, tile);
                action.previousBlock = info.previousBlock;
                action.previousRotation = info.previousRotation;
                action.previousConfig = info.previousConfig;
                action.constructBlock = tile.block();
                action.constructRotation = tile.rotation();
                actionLog.add(action);
            }

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.blocksConstructed++;

            if (debug) {
                sendMessage("[cyan]Debug[] " + formatPlayer(targetPlayer) + " builds [accent]" +
                        tile.block().name + "[] at " + formatTile(tile), false);
            }
        }
    }

    public void handleBlockDeconstructProgress(Player builder, Tile tile, Block cblock, float progress, Block previous) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (builder != null) info.deconstructedBy = builder;

        if (!info.deconstructSeen) {
            info.deconstructSeen = true;

            Actions.Deconstruct action = new Actions.Deconstruct(builder, tile);
            action.previousBlock = info.previousBlock;
            action.previousRotation = info.previousRotation;
            action.previousConfig = info.previousConfig;
            actionLog.add(action);
        }
    }

    public void handleBlockDeconstructFinish(Tile tile, Block block, int builderId) {
        // this runs before the block is actually removed
        TileInfo info = getOrCreateTileInfo(tile);
        Player targetPlayer = playerGroup.getByID(builderId);
        if (targetPlayer != null) info.deconstructedBy = targetPlayer;
        info.reset();
        tile.getLinkedTiles(linked -> getOrCreateTileInfo(linked, false).unlink());

        if (targetPlayer != null) {
            if (!info.deconstructSeen) {
                Actions.Deconstruct action = new Actions.Deconstruct(targetPlayer, tile);
                action.previousBlock = info.previousBlock;
                action.previousRotation = info.previousRotation;
                action.previousConfig = info.previousConfig;
                actionLog.add(action);
            }

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.blocksBroken++;

            if (debug) {
                sendMessage("[cyan]Debug[] " + targetPlayer.name + "[white] ([stat]#" + builderId +
                        "[]) deconstructs [accent]" + tile.block().name + "[] at " + formatTile(tile), false);
            }
        }
    }

    public Player getNearestPlayerByLocation(float x, float y) {
        // grab every player in a 10x10 area and then find closest
        Array<Player> candidates = playerGroup.intersect(x - 5, y - 5, 10, 10);
        if (candidates.size == 0) return null;
        if (candidates.size == 1) return candidates.first();
        if (candidates.size > 1) {
            float nearestDistance = Float.MAX_VALUE;
            Player nearestPlayer = null;
            for (Player player : candidates) {
                float distance = Mathf.dst(x, y, player.x, player.y);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
            return nearestPlayer;
        }
        return null; // this should be impossible
    }

    public void handleDeposit(DepositEvent event) {
        Player targetPlayer = event.player;
        Tile tile = event.tile;
        Item item = event.item;
        int amount = event.amount;
        if (targetPlayer == null) return;
        if (verbose) {
            sendMessage("[green]Verbose[] " + targetPlayer.name + "[white] ([stat]#" + targetPlayer.id +
                "[]) transfers " + amount + " " + item.name + " to " + tile.block().name + " " + formatTile(tile), false);
        }

        Actions.DepositItems action = new Actions.DepositItems(targetPlayer, tile);
        action.item = item;
        action.amount = amount;
        actionLog.add(action);

        if (item.equals(Items.thorium) && tile.block() instanceof NuclearReactor) {
            String message = "[scarlet]WARNING[] " + targetPlayer.name + "[white] ([stat]#" +
                targetPlayer.id + "[]) transfers [accent]" + amount + "[] thorium to a reactor. " + formatTile(tile);
            sendMessage(message);
        } else if (item.explosiveness > 0.5f) {
            Block block = tile.block();
            if (block instanceof ItemLiquidGenerator) {
                String message = "[scarlet]WARNING[] " + formatPlayer(targetPlayer) + " transfers [accent]" +
                    amount + "[] blast to a generator. " + formatTile(tile);
                sendMessage(message);
            } else if (block instanceof Vault) {
                String message = "[scarlet]WARNING[] " + formatPlayer(targetPlayer) + " transfers [accent]" +
                        amount + "[] blast to a Vault. " + formatTile(tile);
                sendMessage(message);
            } else if (block instanceof StorageBlock) {
                String message = "[scarlet]WARNING[] " + formatPlayer(targetPlayer) + " transfers [accent]" +
                    amount + "[] blast to a Container. " + formatTile(tile);
                sendMessage(message);
            }
        }
    }

    public void handleWithdraw(WithdrawEvent event) {
        Actions.WithdrawItems action = new Actions.WithdrawItems(event.player, event.tile);
        action.item = event.item;
        action.amount = event.amount;
        actionLog.add(action);
    }

    public void handlePlayerEntitySnapshot(Player targetPlayer) {
        // System.out.println("received entity snapshot for " + targetPlayer.name + "#" + targetPlayer.id);
        // System.out.println("entity previous: " + playerStats.get(targetPlayer));
        PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
        if (debug) {
            sendMessage("[cyan]Debug[] Player snapshot: " + targetPlayer.name + "[white] ([stat]#" + targetPlayer.id + "[])", false);
        }
    }

    public void handlePlayerDisconnect(int playerId) {
        Player targetPlayer = playerGroup.getByID(playerId);
        // System.out.println("player disconnect: " + targetPlayer.name + "#" + targetPlayer.id);
        PlayerStats stats = playerStats.get(targetPlayer);
        if (stats != null) {
            stats.gone = true;
            String traceString = "";
            if (stats.trace != null) traceString = " " + formatTrace(stats.trace);
            // custom
            // sendLocal("[accent]Player leave:[] " + formatPlayer(targetPlayer) + traceString);
        }
    }

    public void handleWorldDataBegin() {
        playerStats.clear();
    }

    public String formatPlayer(Player target) {
        String playerString;
        if (target != null) {
            int ref = refs.get(target);
            playerString = target.name + "[white] ([stat]#" + target.id + " &" + ref + "[])";
        } else {
            playerString = "[lightgray]unknown[]";
        }
        return playerString;
    }

    public String formatColor(Color color) {
        return "[#" + Integer.toHexString(((int)(255 * color.r) << 24) | ((int)(255 * color.g) << 16) | ((int)(255 * color.b) << 8)) + "]";
    }

    public String formatColor(Color color, String toFormat) {
        return formatColor(color) + toFormat + "[]";
    }

    public String formatItem(Item item) {
        if (item == null) return "(none)";
        return formatColor(item.color, item.name);
    }

    public String formatTile(Tile tile) {
        if (tile == null) return "(none)";
        return "(" + tile.x + ", " + tile.y + ")";
    }

    public String formatRatelimit(Ratelimit rl) {
        return (rl.check() ? "exceeded" : "not exceeded") + " (" + rl.events() + " events in " + rl.findTime + " ms)";
    }

    public String formatRatelimit(Ratelimit rl, Player source) {
        return (rl.check() ? "exceeded" : "not exceeded") + " for " + formatPlayer(source) + " (" + rl.events() + " events in " + rl.findTime + " ms)";
    }

    public String formatTrace(TraceInfo trace) {
        if (trace == null) return "(trace not available)";
        return "ip=" + trace.ip + " uuid=" + trace.uuid + " mobile=" + trace.mobile;
    }

    public void handlePowerGraphSplit(Player targetPlayer, Tile tile, PowerGraph oldGraph, PowerGraph newGraph1, PowerGraph newGraph2) {
        int oldGraphCount = oldGraph.all.size;
        int newGraph1Count = newGraph1.all.size;
        int newGraph2Count = newGraph2.all.size;

        if (Math.min(oldGraphCount - newGraph1Count, oldGraphCount - newGraph2Count) > 100) {
            sendMessage("[lightgray]Notice[] Power split by " + formatPlayer(targetPlayer) + " " + oldGraphCount + " -> " +
                newGraph1Count + "/" + newGraph2Count + " " + formatTile(tile));
        }
    }

    public void handleBlockBeforeConfigure(Tile tile, Player targetPlayer, int value) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (targetPlayer != null) {
            info.logInteraction(targetPlayer);

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.configureCount++;
            // don't trip on auto item source config
            if (tile.block() != Blocks.itemSource && stats.configureRatelimit.get()) {
                stats.configureRatelimit.nextTick(rl -> sendMessage("[scarlet]WARNING[] Configure ratelimit " + formatRatelimit(rl, targetPlayer)));
            }
        }

        Block block = tile.block();
        if (block.posConfig) {
            Actions.ConfigurePositional action = new Actions.ConfigurePositional(targetPlayer, tile);
            action.targetBlock = tile.block();
            action.beforeConfig = tile.entity.config();
            action.afterConfig = value;
            actionLog.add(action);
        } else if (block instanceof ItemSource || // excuse hard coded values
                block instanceof Sorter ||
                block instanceof Unloader ||
                block instanceof LiquidSource) {
            Actions.ConfigureItemSelect action = new Actions.ConfigureItemSelect(targetPlayer, tile);
            action.targetBlock = tile.block();
            action.beforeConfig = tile.entity.config();
            action.afterConfig = value;
            actionLog.add(action);
        } else if (block instanceof PowerNode) {
            Actions.ConfigurePowerNode action = new Actions.ConfigurePowerNode(targetPlayer, tile);
            action.disconnect = tile.entity.power.links.contains(value); // should never be null, if not then oh no
            action.other = value;
            actionLog.add(action);
        }
    }
    
    public void handleRotateBlock(Player targetPlayer, Tile tile, boolean direction) {
        TileInfo info = getOrCreateTileInfo(tile);
        if (targetPlayer != null) {
            info.lastRotatedBy = targetPlayer;
            info.logInteraction(targetPlayer);

            PlayerStats stats = getOrCreatePlayerStats(targetPlayer);
            stats.rotateCount++;
            if (stats.rotateRatelimit.get()) {
                stats.rotateRatelimit.nextTick(rl -> sendMessage("[scarlet]WARNING[] Rotate ratelimit " + formatRatelimit(rl, targetPlayer)));
            }
        }

        if (verbose) {
            sendMessage("[green]Verbose[] " + formatPlayer(targetPlayer) + " rotates " +
                tile.block().name + " at " + formatTile(tile));
        }

        Actions.RotateBlock action = new Actions.RotateBlock(targetPlayer, tile);
        action.targetBlock = tile.block();
        action.beforeRotation = tile.rotation();
        action.direction = direction;
        actionLog.add(action);
    }

    public void handleTileTapped(Player target, Tile tile) {
        /* this is not a good idea
        Actions.TapTile action = new Actions.TapTile(target, tile);
        actionLog.add(action);
        */
    }

    public void handleThoriumReactorHeat(Tile tile, float heat) {
        if (heat > 0.15f && tile.interactable(player.getTeam())) {
            sendMessage("[scarlet]WARNING[] Thorium reactor at " + formatTile(tile) + " is overheating! Heat: [accent]" + heat);
        }
    }

    public boolean doAutoban(Player targetPlayer, String reason) {
        if (player.isAdmin && targetPlayer != null && autoban) {
            Call.onAdminRequest(targetPlayer, AdminAction.ban);
            String message = "[yellow]Autoban[] Banning player " + formatPlayer(targetPlayer);
            if (reason != null) message += " (" + reason + ")";
            sendMessage(message, false);
            return true;
        } else return false;
    }

    public TileInfo getTileInfo(Tile tile) {
        TileInfo info = tileInfo.get(tile);
        if (info != null && info.link != null) info = info.link;
        return info;
    }

    public void handleMessageBlockText(Player targetPlayer, Tile tile, String text) {
        // TODO: maybe log what the text said?
        if (targetPlayer == null) return;
        TileInfo info = getOrCreateTileInfo(tile);
        info.logInteraction(targetPlayer);
    }

    /**
     * AdminAction trace result hook
     * @param target
     * @param info
     * @return True if trace result ui should be inhibited, false otherwise
     */
    public boolean handleTraceResult(Player target, TraceInfo info) {
        PlayerStats stats = playerStats.get(target);
        if (stats == null) return false;
        boolean requested = stats.autoTraceRequested;
        stats.handleTrace(info);
        return requested;
    }

    public void loadComplete() {
        auto = new Auto();
    }

    public String getCountry(String ip) {
        try {

            URL url = new URL("https://freegeoip.app/json/" + ip);

            // read text returned by server
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));

            String line = in.readLine();
            in.close();
            JSONObject obj = new JSONObject(line);
            String country = obj.getString("country_name");
            String city = obj.getString("region_name");
            return "\n[lime]" + country + " " + city;
        }
        catch (MalformedURLException e) {
            System.out.println("Malformed URL: " + e.getMessage());
        }
        catch (IOException e) {
            System.out.println("I/O Error: " + e.getMessage());
        }
        return null;
    }

    public boolean getIPLevel(String ip) {
        try{
            String url = "http://v2.api.iphub.info/ip/" + ip;
            String[] commands = {"curl", url, "-H", "X-Key: MTAwMzE6MFdaT2V1RHBPTWx0bTl6VDFzUkJybmxpWUFUTXRjclk="};
            Process process = Runtime.getRuntime().exec(commands);
            BufferedReader reader = new BufferedReader(new
                    InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            JSONObject obj = new JSONObject(line);
            int block = obj.getInt("block");
            if(block == 1) {
                return true;
            }
            else{
                return false;
            }
        }
        catch(IOException e) {
            return false;
        }
    }
    public boolean getNameLevel(String name) {
        return name.matches(".*[^a-zA-Z0-9 \\[\\]].*");
    }
}
