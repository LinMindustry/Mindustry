package mindustry.game.griefprevention;

import arc.Core;
import arc.struct.Queue;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Interval;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.entities.traits.BuilderTrait;
import mindustry.entities.traits.BuilderTrait.BuildRequest;
import mindustry.entities.type.Player;
import mindustry.entities.type.SolidEntity;
import mindustry.entities.type.TileEntity;
import mindustry.entities.type.Unit;
import mindustry.gen.Call;
import mindustry.input.Binding;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.ItemType;
import mindustry.world.Tile;
import mindustry.world.blocks.sandbox.ItemSource.ItemSourceEntity;
import mindustry.world.modules.ItemModule;

import java.lang.reflect.Field;

import static arc.Core.camera;
import static mindustry.Vars.*;

/* Auto mode */
public class Auto {
    public enum Mode { GotoTile, GotoEntity, AssistEntity, UndoEntity }

    public boolean enabled = true;
    public boolean movementActive = false;
    public Mode mode;
    public boolean persist = false;
    public float targetDistance = 0.0f;
    public boolean freecam = false;

    public Tile targetTile;
    public Unit targetEntity;
    public Tile targetItemSource;
    public Tile autoDumpTarget;
    public Tile autoPickupTarget;
    public Item autoPickupTargetItem;
    public Vec2 cameraTarget = new Vec2();

    public float targetEntityLastRotation;

    public Interval timer = new Interval(3);
    public static final int votekickWaitTimer = 0;
    public static final int itemTransferTimer = 1;
    public static final int requestItemTimer = 2;

    public Vec2 movement;
    public Vec2 velocity;

    public boolean movementControlled = false;
    public boolean shootControlled = false;
    public boolean overrideCamera = false;

    public boolean wasAutoShooting = false;

    public Field itemSourceEntityOutputItemField;

    public Auto() {
        try {
            Class<Player> playerClass = Player.class;
            Field playerMovementField = playerClass.getDeclaredField("movement");
            playerMovementField.setAccessible(true);
            movement = (Vec2)playerMovementField.get(player);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException("reflective access failed on Player.movement");
        }
        try {
            Class<SolidEntity> solidEntityClass = SolidEntity.class;
            Field playerVelocityField = solidEntityClass.getDeclaredField("velocity");
            playerVelocityField.setAccessible(true);
            velocity = (Vec2)playerVelocityField.get(player);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException("reflective access failed on SolidEntity.velocity");
        }

        try {
            Class<ItemSourceEntity> itemSourceEntityClass = ItemSourceEntity.class;
            itemSourceEntityOutputItemField = itemSourceEntityClass.getDeclaredField("outputItem");
            itemSourceEntityOutputItemField.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("reflective access failed on ItemSourceEntity.outputItem");
        }
    }

    public void gotoTile(Tile tile, float distance) {
        movementActive = true;
        mode = Mode.GotoTile;
        targetTile = tile;
        targetDistance = distance;
        persist = false;
    }

    public void gotoEntity(Unit unit, float distance, boolean follow) {
        movementActive = true;
        mode = Mode.GotoEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = follow;
    }

    public void assistEntity(Unit unit, float distance) {
        movementActive = true;
        mode = Mode.AssistEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public void undoEntity(Unit unit, float distance) {
        movementActive = true;
        mode = Mode.UndoEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public boolean manageItemSource(Tile tile) {
        if (tile == null) {
            targetItemSource = null;
            return true;
        }
        if (tile.block() != Blocks.itemSource) return false;
        targetItemSource = tile;
        return true;
    }

    public boolean setAutoDumpTransferTarget(Tile tile) {
        if (tile == null) {
            autoDumpTarget = null;
            return true;
        }
        if (!tile.block().hasItems || !tile.interactable(player.getTeam())) return false;
        autoDumpTarget = tile;
        return true;
    }

    public boolean setAutoPickupTarget(Tile tile, Item item) {
        if (tile == null) {
            autoPickupTarget = null;
            autoPickupTargetItem = null;
            return true;
        }
        if (!tile.block().hasItems || !tile.interactable(player.getTeam())) return false;
        autoPickupTarget = tile;
        autoPickupTargetItem = item;
        return true;
    }

    public void setFreecam(boolean enable) {
        setFreecam(enable, player.x, player.y);
    }

    public void setFreecam(boolean enable, float x, float y) {
        if (enable) {
            cameraTarget.set(x, y);
            freecam = true;
        } else {
            freecam = false;
        }
    }

    /** whether default camera handling should be disabled */
    public boolean cameraOverride() {
        return overrideCamera || freecam;
    }

    /** whether default movement handling should be disabled */
    public boolean movementOverride() {
        return freecam;
    }

    public void update() {
        if (!enabled) return;

        updateItemSourceTracking();
        updateAutoDump();
        updateAutoPickup();
        updateMovement();
        updateCamera();
        updateControls();
    }

    public void updateAutoDump() {
        Tile tile = autoDumpTarget;
        if (tile == null || !tile.block().hasItems || !tile.interactable(player.getTeam())) {
            // tile doesn't accept items, reset the thing
            autoDumpTarget = null;
            return;
        }
        ItemStack stack = player.item();
        // if (!timer.get(itemTransferTimer, 50)) return;
        if (stack.amount > 0 &&
                tile.block().acceptStack(stack.item, stack.amount, tile, player) > 0 &&
                !player.isTransferring) {
            Call.transferInventory(player, tile);
        }
    }

    public void updateAutoPickup() {
        Tile tile = autoPickupTarget;
        Item item = autoPickupTargetItem;
        if (tile == null || !tile.block().hasItems || !tile.interactable(player.getTeam()) || item == null) {
            // tile doesn't accept items, reset the thing
            autoPickupTarget = null;
            autoPickupTargetItem = null;
            return;
        }
        ItemStack stack = player.item();
        if (stack.amount > 0 && stack.item != item) return;
        int amount = player.mech.itemCapacity - stack.amount;
        amount = Math.min(amount, tile.entity.items.get(item));
        if (amount == 0) return;
        // if (!timer.get(requestItemTimer, 50)) return;
        Call.requestItem(player, tile, item, amount);
    }

    public void updateItemSourceTracking() {
        if (targetItemSource == null) return;
        if (targetItemSource.block() != Blocks.itemSource) {
            griefWarnings.sendMessage("[gray]Notice[] Item source " + griefWarnings.formatTile(targetItemSource) + " gone");
            targetItemSource = null;
            return;
        }
        TileEntity core = player.getClosestCore();
        if (core == null) return;
        ItemModule items = core.items;

        Item least = null;
        int count = Integer.MAX_VALUE;
        for (int i = 0; i < content.items().size; i++) {
            Item currentItem = content.item(i);
            if (currentItem.type != ItemType.material) continue;
            int currentCount = items.get(currentItem);
            if (currentCount < count) {
                least = currentItem;
                count = currentCount;
            }
        }
        ItemSourceEntity entity = targetItemSource.ent();
        Item currentConfigured;
        try {
            currentConfigured = (Item)itemSourceEntityOutputItemField.get(entity);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("reflective access failed on ItemSourceEntity.outputItem");
        }
        if (least != null && least != currentConfigured) targetItemSource.configure(least.id);
    }

    public void updateMovement() {
        if (!movementActive) return;
        float speed = !player.mech.flying
                ? player.mech.boostSpeed
                : player.mech.speed;

        float targetX;
        float targetY;
        switch (mode) {
            case GotoTile:
                if (targetTile == null) {
                    movementActive = false;
                    return;
                }
                targetX = targetTile.getX();
                targetY = targetTile.getY();
                break;
            case GotoEntity:
            case AssistEntity:
            case UndoEntity:
                if (targetEntity == null) {
                    movementActive = false;
                    return;
                }
                targetX = targetEntity.x;
                targetY = targetEntity.y;
                break;
            default:
                throw new RuntimeException("invalid mode");
        }

        movementControlled = false;
        if (player.dst(targetX, targetY) < targetDistance) {
            movement.setZero();
            if (!persist) {
                player.isBoosting = false;
                cancelMovement();
            }
        } else if (!Core.input.keyDown(Binding.suspend_movement)) { // allow suspend of movement by holding down key
            player.isBoosting = true;
            movement.set(
                    (targetX - player.x) / Time.delta(),
                    (targetY - player.y) / Time.delta()
            ).limit(speed);
            movement.setAngle(Mathf.slerp(movement.angle(), velocity.angle(), 0.05f));
            velocity.add(movement.scl(Time.delta()));
            movementControlled = true;
        }

        shootControlled = false;
        assistBlock:
        if (mode == Mode.AssistEntity) {
            if (targetEntity instanceof Player) {
                Player targetPlayer = (Player)targetEntity;
                // crappy is shooting logic
                if (!targetPlayer.getTimer().check(targetPlayer.getShootTimer(false), targetPlayer.getWeapon().reload * 1.25f)) {
                    player.buildQueue().clear();
                    player.isBuilding = false;
                    player.isShooting = true;
                    wasAutoShooting = true;
                    shootControlled = true;

                    player.rotation = Mathf.slerpDelta(player.rotation, targetEntityLastRotation, 0.1f * player.mech.getRotationAlpha(player));
                    float rotationDeg = targetEntityLastRotation * Mathf.degreesToRadians;
                    player.pointerX = player.getX() + 200 * Mathf.cos(rotationDeg);
                    player.pointerY = player.getY() + 200 * Mathf.sin(rotationDeg);
                    break assistBlock;
                } else if (wasAutoShooting) {
                    player.isShooting = false;
                    wasAutoShooting = false;
                }
            }
            if (targetEntity instanceof BuilderTrait) {
                BuilderTrait targetBuildEntity = (BuilderTrait)targetEntity;
                BuildRequest targetRequest = targetBuildEntity.buildRequest();
                if (targetRequest != null) {
                    Queue<BuildRequest> buildQueue = player.buildQueue();
                    buildQueue.clear();
                    buildQueue.addFirst(targetRequest);
                    player.isBuilding = true;
                    player.isShooting = false;
                    break assistBlock;
                }
            }
        } else if (mode == Mode.UndoEntity) {
            if (targetEntity instanceof BuilderTrait) {
                BuilderTrait targetBuildEntity = (BuilderTrait) targetEntity;
                // TODO: handle configures
                BuildRequest targetRequest = targetBuildEntity.buildRequest();
                if (targetRequest != null) {
                    BuildRequest undo;
                    if (targetRequest.breaking) {
                        Tile target = world.tile(targetRequest.x, targetRequest.y);
                        undo = new BuildRequest(targetRequest.x, targetRequest.y, target.rotation(), target.block());
                    } else undo = new BuildRequest(targetRequest.x, targetRequest.y);
                    player.buildQueue().addLast(undo);
                    player.isBuilding = true;
                }
            }
        }

        if(velocity.len() <= 0.2f && player.mech.flying){
            player.rotation += Mathf.sin(Time.time() + player.id * 99, 10f, 1f);
        }else if(player.target == null){
            player.rotation = Mathf.slerpDelta(player.rotation, velocity.angle(), velocity.len() / 10f);
        }
        player.updateVelocityStatus();
    }

    /** Custom camera handling, if enabled */
    public void updateCamera() {
        if (!cameraOverride()) return;
        if (freecam && !ui.chatfrag.shown()) {
            float camSpeed = !Core.input.keyDown(Binding.dash) ? 10f : 25f;
            cameraTarget.add(Tmp.v1.setZero().add(Core.input.axis(Binding.move_x), Core.input.axis(Binding.move_y)).nor().scl(Time.delta() * camSpeed));

            if(Core.input.keyDown(Binding.mouse_move)){
                cameraTarget.x += Mathf.clamp((Core.input.mouseX() - Core.graphics.getWidth() / 2f) * 0.005f, -1, 1) * camSpeed;
                cameraTarget.y += Mathf.clamp((Core.input.mouseY() - Core.graphics.getHeight() / 2f) * 0.005f, -1, 1) * camSpeed;
            }
        }

        camera.position.lerpDelta(cameraTarget, 0.08f);
    }

    public void updateControls() {
        if (Core.scene.hasKeyboard()) return;
        // if (Core.input.keyTap(Binding.freecam)) setFreecam(!freecam);
    }

    /** Perform necessary cleanup after stopping */
    public void cancelMovement() {
        movementActive = false;
        persist = false;
        targetTile = null;
        targetEntity = null;
        movementControlled = false;
        shootControlled = false;
        wasAutoShooting = false;
    }

    public void reset() {
        cancelMovement();
        targetItemSource = null;
        autoDumpTarget = null;
        autoPickupTarget = null;
        overrideCamera = false;
    }

    public void handlePlayerShoot(Player target, float offsetX, float offsetY, float rotation) {
        if (target == targetEntity) targetEntityLastRotation = rotation;
    }

    public boolean interceptMessage(String message, String sender, Player playersender) {
        // message is annoying
        if (message.startsWith("[scarlet]You must wait ") && sender == null) {
            return !timer.get(votekickWaitTimer, 90);
        }
        return false;
    }

    public void votekick(String identifier) {
        Player p = griefWarnings.commandHandler.getPlayer(identifier);
        if (p == null) return;
        Call.sendChatMessage("/votekick " + p.name);
    }
}
