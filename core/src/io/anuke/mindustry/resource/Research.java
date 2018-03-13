package io.anuke.mindustry.resource;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Bundles;

public class Research {
    public static final Array<Research> researches = new Array<>();

    public static final Research
            unlockOmnidrill = new Research("unlockOmnidrill", null, stack(Item.dirium,2)),
            unlockFluxpump = new Research("unlockFluxpump", null, stack(Item.titanium, 2));

    public final int id;
    public final String name;
    public ItemStack[] requirements;
    public String description;
    public TextureRegion region;
    public boolean researched;
    public Research unlock;

    public Research(String name, Research unlock, ItemStack... requirements) {
        this.id = researches.size;
        this.name = name;
        this.requirements = requirements;
        this.unlock = unlock;
        this.description = Bundles.get("research."+name+".description");

        researches.add(this);
    }

    public void init(){
        this.region = Draw.region("research/"+name);
    }

    public String localizedName(){
        return Bundles.get("research." + this.name + ".name");
    }

    @Override
    public String toString() {
        return localizedName();
    }

    private static ItemStack stack(Item item, int amount){
        return new ItemStack(item, amount);
    }

}