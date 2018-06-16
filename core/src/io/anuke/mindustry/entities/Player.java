package io.anuke.mindustry.entities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.graphics.Fx;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.NetEvents;
import io.anuke.mindustry.resource.Mech;
import io.anuke.mindustry.resource.Upgrade;
import io.anuke.mindustry.resource.Weapon;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Blocks;
import io.anuke.ucore.core.*;
import io.anuke.ucore.entities.BulletEntity;
import io.anuke.ucore.entities.SolidEntity;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Timer;
import io.anuke.ucore.util.Translator;
import io.anuke.mindustry.entities.effect.Shield;

import java.nio.ByteBuffer;

import static io.anuke.mindustry.Vars.*;

public class Player extends SyncEntity{
	static final float speed = 1.1f;
	static final float dashSpeed = 1.8f;
	static final int timerDash = 0;
	static final int timerRadiation = 1;
	static final int timerOther = 2;
	static final int timerRegen = 3;
    private int cx = 0;
	public String name = "name";
	public String unformatedName = "name";
	public boolean isFlying;
	public boolean isAndroid;
	public boolean isAdmin;
	public Color color = new Color();
	public float radiation;
    public int radiationDeath = 255;
	public Weapon weaponLeft = Weapon.blaster;
	public Weapon weaponRight = Weapon.blaster;
	public Mech mech = Mech.standard;

	public float targetAngle = 0f;
	public boolean dashing = false;
	public int flyCooldown = 0;

	public int clientid = -1;
	public boolean isLocal = false;
	public Timer timer = new Timer(4);
    public Timer shootTimer = new Timer(4);

	private Vector2 movement = new Vector2();
	private Translator tr = new Translator();

	public boolean carry = false;
	public Player carrier;

	public boolean walking = true;
	public int jumpHeightMultiplier = 1;
	public float gravity = 0.3f;
	private float oldy = 0;

	private float legframe = 1f;
	public Shield personalshield = new Shield((int)x,(int)y);

	//Debug
	public float movementx;
	public float movementy;


	public Player(){
		hitbox.setSize(5);
		hitboxTile.setSize(4f);
		
		maxhealth = 200;
		heal();
        radiation=0;
	}

	@Override
	public void damage(float amount){
		if(debug || isFlying) return;

		health -= amount;
		if(health <= 0 && !dead && isLocal){ //remote players don't die normally
			onDeath();
			dead = true;
		}
	}
	
	@Override
	public void onDeath(){
		dead = true;
		if(Net.active()){
			NetEvents.handlePlayerDeath();
		}
        radiation=0;
		Effects.effect(Fx.explosion, this, dimension);
		Effects.shake(4f, 5f, this);
		Effects.sound("die", this);

		control.setRespawnTime(respawnduration);
		ui.hudfrag.fadeRespawn(true);
		ui.hudfrag.resetColor();
		//dimension = (dimension==1 ? 0 : 1);
	}

	/**called when a remote player death event is recieved*/
	public void doRespawn(){
		dead = true;
		Effects.effect(Fx.explosion, this, dimension);
		Effects.shake(4f, 5f, this);
		Effects.sound("die", this);

		Timers.run(respawnduration + 5f, () -> {
			heal();
            radiation=0;
			set(world[dimension].getSpawnX(), world[dimension].getSpawnY());
			interpolator.target.set(x, y);
		});
	}
	
	@Override
	public void drawSmooth(){
		if((debug && (!showPlayer || !showUI)) || (isAndroid && isLocal) || dead) return;
        boolean snap = snapCamera && Settings.getBool("smoothcam") && Settings.getBool("pixelate") && isLocal;

		String part = isFlying ? "ship" : "mech";
		mech = walking ? mech.walk : mech.standard;

		Shaders.outline.color.set(getColor());
		Shaders.outline.lighten = 0f;
		Shaders.outline.region = Draw.region(part + "-" + mech.name);

		Shaders.outline.apply();

		if(!isFlying && !walking) {
			for (int i : Mathf.signs) {
				Weapon weapon = i < 0 ? weaponLeft : weaponRight;
				tr.trns(angle - 90, weapon.dx*i, weapon.dy);
				float w = i > 0 ? -8 : 8;
				if(snap){
					Draw.rect(weapon.name + "-equip", (int)x + tr.x, (int)y + tr.y + (walking ? 10 : 0), w, weapon.dh, angle - 90);
                }else{
					Draw.rect(weapon.name + "-equip", x + tr.x, y + tr.y + (walking ? 10 : 0), w, weapon.dh, angle - 90);
				}
			}
		}
        if(snap && walking && !isFlying){
            Draw.rect(part + "-" + mech.name + "-leg-" + Math.round(legframe) + (angle>90&&angle<270 ? "L":""), (int)x, (int)y-4, 0);
        }else if (walking && !isFlying) {
			Draw.rect(part + "-" + mech.name + "-leg-" + Math.round(legframe) + (angle > 90 && angle < 270 ? "L" : ""), x, y - 4, 0);
		}

		if(snap){

			Draw.rect(part + "-" + mech.name, (int)x, (int)y + (walking ? 10 : 0)-4, angle-90);
			Draw.rect(weaponLeft.name + "-equip", (int)x, y, 0, weaponLeft.dh, angle - 90);
		}else{
			Draw.rect(part + "-" + mech.name, x, y + (walking ? 10 : 0)-4, angle-90);
			Draw.rect(weaponLeft.name + "-equip", x, y, 0, weaponLeft.dh, angle - 90);
		}


		Graphics.flush();
	}
	
	@Override
	public void update(){
		if(isFlying && walking) {
			Effects.effect(Fx.hoverSmoke, x + 0 + Angles.trnsx(angle - 10f + 225f, 6f), y + 6 + Angles.trnsy(angle - 10f + 225f, 6f), this.dimension);
			Effects.effect(Fx.hoverSmoke, x + 0 + Angles.trnsx(angle + 10f + 135f, 6f), y + 6 + Angles.trnsy(angle +10f + 135f, 6f), this.dimension);
		}
		if(walking && !isFlying) {
            hitbox.setSize(5, 22);
            hitboxTile.setSize(5f, 22f);
        }else {
            hitbox.setSize(5);
            hitboxTile.setSize(5f);
        }
        if(!isLocal || isAndroid){
			if(isAndroid && isLocal){
				angle = Mathf.slerpDelta(angle, targetAngle, 0.2f);
			}
			if(!isLocal) interpolate();
			return;
		}

		if(isDead()) return;

		if(flyCooldown>0) flyCooldown-=1;

		Tile tile = world[dimension].tileWorld(x, y);
		Block block = tile.floorOrBlock();

		if(!isFlying) {

			boolean timerRad = timer.get(timerRadiation,3);

			if(timerRad) {
				if (radiation > 0 && !tile.floor().radioactive) {
					radiation -= 1;
				}

				if (tile.floor().radioactive) {
					radiation += tile.floor().radioactivity;
				}
				if (radiation >= radiationDeath) {
					damage(health + 1);
				}
				damage(radiation / 255);
			}
		}
        
		//if player is in solid block
		if(tile != null && (((tile.floor().liquid && tile.solid()) && tile.block() == Blocks.air) || tile.solid())) {
			damage(health + 1); //die instantly
		}

		if(ui.chatfrag.chatOpen()) return;


		ui.hudfrag.setAlpha(Mathf.clamp((float)global.time/Vars.maxTime,0f,0.7f));

        if(radiation>0){
            ui.hudfrag.setGreen(radiation/255);
        }

        if(health<100){
			ui.hudfrag.setRed(1.0f-health/100);
		}

		int damageOnTop = block.damageOnTop;

		if(damageOnTop > 0)
			damage(damageOnTop);

		float speedModifier;
		speedModifier = block.movementSpeedMultiplier;

		float speed;
		dashing = Inputs.keyDown("dash");

		if (!isFlying) {
			speed = dashing ? (debug ? Player.dashSpeed * 5f : Player.dashSpeed) : Player.speed;
			speed *= speedModifier;
		}
		else
		{
			speed = dashing ? Player.dashSpeed * 3f : Player.dashSpeed;
		}

		if(health < maxhealth && timer.get(timerRegen, 20))
			health ++;
        

		health = Mathf.clamp(health, -1, maxhealth);

		if (walking && !isFlying){
			if (y != oldy)
				movement.y = movement.y - gravity/8 * Timers.delta();
			else
				movement.y = -0.01f;
			movement.x = 0;
		}

        else if(block.icePhysics)
            movement.set(movement.x*block.iceMovementModifier, movement.y*block.iceMovementModifier);
		else
			movement.set(0, 0);
        
        float xa = Inputs.getAxis("move_x");
		float ya = Inputs.getAxis("move_y");
		if(Math.abs(xa) < 0.3) xa = 0;
		if(Math.abs(ya) < 0.3) ya = 0;

		if (!isFlying && walking && y == oldy && ya != 0 && movement.y == -0.01f)
			movement.y = 1.75f * jumpHeightMultiplier;
		else if (!walking)
			movement.y += ya*speed;
		if(isFlying)
			movement.y += ya*speed;
		if (xa != 0) {
            movement.x += xa * speed;
			if(angle>90&&angle<270)
				legframe += movement.x < 0 ? 0.25f : -0.25f;
			else
				legframe += movement.x > 0 ? 0.25f : -0.25f;
            if (Math.round(legframe)==7)
                legframe = 1f;
            if (Math.round(legframe)==0)
            	legframe = 6f;
		}

		boolean shooting = !Inputs.keyDown("dash") && Inputs.keyDown("shoot") && control.input().recipe == null
				&& !ui.hasMouse() && !control.input().onConfigurable() && !isFlying;
		if(shooting && weaponLeft != Weapon.portableshieldgenerator){
			weaponLeft.update(player, true);
			weaponRight.update(player, false);
		}else if(weaponLeft == Weapon.portableshieldgenerator){
			this.personalshield.x = this.x;
			this.personalshield.y = this.y;
			this.personalshield.radius = 10;
		}
		
		if(dashing && timer.get(timerDash, 3) && movement.len() > 0){
			Effects.effect(Fx.dashsmoke, x + Angles.trnsx(angle + 180f, 3f), y + Angles.trnsy(angle + 180f, 3f), this.dimension);
		}
		
		movement.limit(10);

		if(tile.block().activeMovement && !walking) {
			if (tile.getRotation() == 0) movement.x += 0.5f * block.activeMovementSpeedMultiplier;
			if (tile.getRotation() == 1) movement.y += 0.5f * block.activeMovementSpeedMultiplier;
			if (tile.getRotation() == 2) movement.x -= 0.5f * block.activeMovementSpeedMultiplier;
			if (tile.getRotation() == 3) movement.y -= 0.5f * block.activeMovementSpeedMultiplier;
		}
		movementx = movement.x;
		movementy = movement.y;
		oldy = y;
		if(isFlying) {
			//TODO: Make flying smoother, less responsive but smooth?
			x += movement.x*Timers.delta();
			y += movement.y*Timers.delta();
		}
		else if(noclip){
			x += movement.x*Timers.delta();
			y += movement.y*Timers.delta();
		}else{
			move(movement.x*Timers.delta(), movement.y*Timers.delta());

		}
		if (!walking) {
            if (!shooting) {
                if (!movement.isZero())
                    angle = Mathf.slerpDelta(angle, movement.angle(), 0.13f);
            } else {
                float angle = Angles.mouseAngle(x, y);
                this.angle = Mathf.slerpDelta(this.angle, angle, 0.1f);
            }
        }else{
            this.angle = Angles.mouseAngle(x, y);
        }

		if(!carry && !isAndroid) {
			x = Mathf.clamp(x, 0, world[dimension].width() * tilesize);
			y = Mathf.clamp(y, 0, world[dimension].height() * tilesize);
		}else{
			x = carrier.x;
			y = carrier.y;
		}
	}

	@Override
	public Player add(){
		return add(world[dimension].playerGroup);
	}

    @Override
    public String toString() {
        return "Player{" + id + ", android=" + android + ", local=" + isLocal + ", " + x + ", " + y + "}\n";
    }

	@Override
	public void writeSpawn(ByteBuffer buffer) {
		buffer.put((byte)name.getBytes().length);
		buffer.put(name.getBytes());
		buffer.put(weaponLeft.id);
		buffer.put(weaponRight.id);
		buffer.put(isAndroid ? 1 : (byte)0);
		buffer.put(isFlying ? 1 : (byte)0);
		buffer.putFloat(radiation);
		buffer.put(isAdmin ? 1 : (byte)0);
		buffer.putInt(Color.rgba8888(color));
		buffer.putFloat(x);
		buffer.putFloat(y);
	}

	@Override
	public void readSpawn(ByteBuffer buffer) {
		byte nlength = buffer.get();
		byte[] n = new byte[nlength];
		buffer.get(n);
		name = new String(n);
		weaponLeft = (Weapon) Upgrade.getByID(buffer.get());
		weaponRight = (Weapon) Upgrade.getByID(buffer.get());
		isAndroid = buffer.get() == 1;
		isFlying = buffer.get() == 1;
		radiation = buffer.getFloat();
		isAdmin = buffer.get() == 1;
		color.set(buffer.getInt());
		x = buffer.getFloat();
		y = buffer.getFloat();
		setNet(x, y);
	}

	@Override
	public void write(ByteBuffer data) {
		if(Net.client() || isLocal) {
			data.putFloat(x);
			data.putFloat(y);
		}else{
			data.putFloat(interpolator.target.x);
			data.putFloat(interpolator.target.y);
		}
		data.putFloat(angle);
		data.putFloat(radiation);
		data.putShort((short)health);
		data.put((byte)(dashing ? 1 : 0));
		data.put((byte)(isFlying ? 1 : 0));
	}

	@Override
	public void read(ByteBuffer data, long time) {
		float x = data.getFloat();
		float y = data.getFloat();
		float angle = data.getFloat();
		float radiation = data.getFloat();
		short health = data.getShort();
		byte dashing = data.get();
		byte isFlying = data.get();

		this.radiation = radiation;
		this.health = health;
		this.dashing = dashing == 1;
		this.isFlying = isFlying == 1;

		interpolator.read(this.x, this.y, x, y, angle, time);
	}

	@Override
	public void interpolate() {
		super.interpolate();

		Interpolator i = interpolator;

		float tx = x + Angles.trnsx(angle + 180f, 4f);
		float ty = y + Angles.trnsy(angle + 180f, 4f);

		if(isFlying && i.target.dst(i.last) > 2f && timer.get(timerDash, 1)){
			Effects.effect(Fx.dashsmoke, tx, ty, dimension);
		}

		if(dashing && !dead && timer.get(timerDash, 3) && i.target.dst(i.last) > 1f){
			Effects.effect(Fx.dashsmoke, tx, ty, dimension);
		}
	}

	public Color getColor(){
		return color;
	}
}
