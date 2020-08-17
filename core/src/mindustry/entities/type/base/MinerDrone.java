package mindustry.entities.type.base;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Structs;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Effects;
import mindustry.entities.Units;
import mindustry.entities.traits.MinerTrait;
import mindustry.entities.type.TileEntity;
import mindustry.entities.units.UnitState;
import mindustry.gen.Call;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.type.UnitType;
import mindustry.world.Pos;
import mindustry.world.Tile;

import java.io.*;

import static mindustry.Vars.*;

/** A drone that only mines.*/
public class MinerDrone extends BaseDrone implements MinerTrait{
    protected Item targetItem;
    protected Tile mineTile;

    public final UnitState

    mine = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            TileEntity entity = getClosestCore();

            if(entity == null) return;

            findItem();

            //core full of the target item, do nothing
            if(targetItem != null && entity.block.acceptStack(targetItem, 1, entity.tile, MinerDrone.this) == 0){
                MinerDrone.this.clearItem();
                return;
            }

            //if inventory is full, drop it off.
            if(item.amount >= getItemCapacity() || (targetItem != null && !acceptsItem(targetItem))){
                setState(drop);
            }else{
                if(retarget() && targetItem != null){
                    target = indexer.findClosestOre(x, y, targetItem);
                }

                if(target instanceof Tile){
                    moveTo(type.range / 1.5f);

                    if(dst(target) < type.range && mineTile != target){
                        setMineTile((Tile)target);
                    }

                    if(((Tile)target).block() != Blocks.air){
                        setState(drop);
                    }
                }else{
                    //nothing to mine anymore, core full: circle spawnpoint
                    if(getSpawner() != null){
                        target = getSpawner();

                        circle(40f);
                    }
                }
            }
        }

        public void exited(){
            setMineTile(null);
        }
    },

    drop = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(item.amount == 0 || item.item.type != ItemType.material){
                clearItem();
                setState(mine);
                return;
            }

            target = getClosestCore();

            if(target == null) return;

            TileEntity tile = (TileEntity)target;

            if(dst(target) < type.range){
                if(tile.tile.block().acceptStack(item.item, item.amount, tile.tile, MinerDrone.this) > 0){
                    Call.transferItemTo(item.item, item.amount, x, y, tile.tile);
                }

                clearItem();
                setState(mine);
            }

            circle(type.range / 1.8f);
        }
    };
    // get settings for transparency of the draugs here
    public float opacity1 = Core.settings.getInt("drawDraugsOpacity") / 100f;
    @Override
    public UnitState getStartState(){
        return mine;
    }

    @Override
    public void drawPulver(Tile tile, Item item) {
        if(Mathf.chance(0.06 * Time.delta())){
            Effects.effect(Fx.pulverizeSmallTrans,
                    tile.worldx() + Mathf.range(tilesize / 2f),
                    tile.worldy() + Mathf.range(tilesize / 2f), 0f, item.color);
        }

    }

    @Override
    public void update(){
        super.update();

        updateMining();
    }

    @Override
    protected void updateRotation(){
        if(mineTile != null && shouldRotate() && mineTile.dst(this) < type.range){
            rotation = Mathf.slerpDelta(rotation, angleTo(mineTile), 0.3f);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), 0.3f);
        }
    }

    @Override
    public boolean shouldRotate(){
        //return isMining();
        return false;
    }

    @Override
    public void drawOver(){
        Draw.alpha(opacity1);
        drawMining(opacity1);
        Draw.reset();
    }

    @Override
    public void drawWeapons() {
        Draw.alpha(opacity1);
        super.drawWeapons();
        Draw.reset();
    }

    @Override
    public void drawEngine(float opacity) {
        Draw.alpha(opacity1);
        super.drawEngine(opacity1);
        Draw.reset();
    }

    @Override
    public void drawStats(float opacity) {
        super.drawStats(opacity1);
        Draw.reset();
    }

    @Override
    public void drawLight() {
        Draw.alpha(opacity1);
        super.drawLight();
        Draw.reset();
    }

    @Override
    public void drawShadow(float offsetX, float offsetY) {
        return;
    }

    @Override
    public void draw() {
        Draw.alpha(opacity1);
        super.draw();
        Draw.reset();
    }

    @Override
    public void drawBackItems(float itemtime, boolean number) {
        UnitType unit = content.units().get(0);
        int countsOfDraugs = unitGroup.count(b -> b.getTeam() == player.getTeam() && b.getTypeID().name.equals(unit.typeID.name));
        if (countsOfDraugs>20){
            Draw.alpha(opacity1);
        }else{
            Draw.alpha(0.99f);
        }
        super.drawBackItems(itemtime, number);
        Draw.reset();
    }

    @Override
    public boolean canMine(Item item){
        return type.toMine.contains(item);
    }

    @Override
    public float getMinePower(){
        return type.minePower;
    }

    @Override
    public Tile getMineTile(){
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile){
        mineTile = tile;
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeInt(mineTile == null || !state.is(mine) ? Pos.invalid : mineTile.pos());
    }

    @Override
    public void read(DataInput data) throws IOException{
        super.read(data);
        mineTile = world.tile(data.readInt());
    }

    protected void findItem(){
        TileEntity entity = getClosestCore();
        if(entity == null){
            return;
        }
        targetItem = Structs.findMin(type.toMine, indexer::hasOre, (a, b) -> -Integer.compare(entity.items.get(a), entity.items.get(b)));
    }
}
