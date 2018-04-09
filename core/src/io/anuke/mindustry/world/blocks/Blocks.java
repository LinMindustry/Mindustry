package io.anuke.mindustry.world.blocks;

import io.anuke.mindustry.graphics.Fx;
import io.anuke.mindustry.resource.Item;
import io.anuke.mindustry.resource.ItemStack;
import io.anuke.mindustry.resource.Liquid;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.types.*;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

public class Blocks{
	public static final Block
	
	air = new Block("air"){
		//no drawing here
		public void drawCache(Tile tile){}
		
		//update floor blocks for effects, if needed
		public void draw(Tile tile){}
	},
	
	blockpart = new BlockPart(){

	},
	
	deepwater = new Floor("deepwater"){{
		variants = 0;
		solid = true;
		liquidDrop = Liquid.water;
		liquid = true;
	}},
	
	water = new Floor("water"){{
		movementSpeedMultiplier = 0.5f;
		variants = 0;
		liquidDrop = Liquid.water;
		liquid = true;
	}},

    nucleargunk = new Floor("nucleargunk"){{
        variants = 8;
        solid = false;
        radioactive = true;
    }},
	
	lava = new Floor("lava"){
		{
			variants = 0;
			liquidDrop = Liquid.lava;
			movementSpeedMultiplier = 0.25f;
			damageOnTop = 1;
			liquid = true;
		}
		
		@Override
		public void update(Tile tile){
			if(Mathf.chance(0.001 * Timers.delta())){
				Effects.effect(Fx.lava, tile.worldx() + Mathf.range(5f), tile.worldy() + Mathf.range(5f), tile.dimension);
			}
			
			if(Mathf.chance(0.002 * Timers.delta())){
				Effects.effect(Fx.lavabubble, tile.worldx() + Mathf.range(3f), tile.worldy() + Mathf.range(3f), tile.dimension);
			}
		}
	},
	
	oil = new Floor("oil"){
		{
			variants = 0;
			solid = false;
			movementSpeedMultiplier = 0.15f;
			liquidDrop = Liquid.oil;
			liquid = true;
		}
		
		@Override
		public void update(Tile tile){
			if(Mathf.chance(0.0022 * Timers.delta())){
				Effects.effect(Fx.oilbubble, tile.worldx() + Mathf.range(2f), tile.worldy() + Mathf.range(2f), tile.dimension);
			}
		}
	},
	
	stone = new Floor("stone"){{
		drops = new ItemStack(Item.stone, 1);
		blends = block -> block != this && !(block instanceof Ore);
	}},
	
	blackstone = new Floor("blackstone"){{
		drops = new ItemStack(Item.stone, 1);
	}},
	
	iron = new Ore("iron"){{
		drops = new ItemStack(Item.iron, 1);
	}},
	
	coal = new Ore("coal"){{
		drops = new ItemStack(Item.coal, 1);
	}},
	
	titanium = new Ore("titanium"){{
		drops = new ItemStack(Item.titanium, 1);
	}},
	
	uranium = new Ore("uranium"){{
		drops = new ItemStack(Item.uranium, 1);
		radioactive = true;
	}},
    lead = new Ore("lead"){{
        drops = new ItemStack(Item.lead, 1);
    }},

	copper = new Ore("copper"){{
		drops = new ItemStack(Item.tin, 1);
	}},

	tin = new Ore("tin"){{
		drops = new ItemStack(Item.copper, 1);
	}},

	dirt = new Floor("dirt"){{
		drops = new ItemStack(Item.dirt,1);
	}},
	
	sand = new Floor("sand"){{
		drops = new ItemStack(Item.sand,1);
	}},
	
	ice = new Floor("ice"){{
		icePhysics = true;
		iceMovementModifier = 0.99f;
	}},
	
	snow = new Floor("snow"){},
	
	grass = new Floor("grass"){{
		drops = new ItemStack(Item.dirt,1);
	}},

	moonblock = new StaticBlock("moonblock"){{
		variants = 3;
	}},

	moon = new Floor("moonfloor"){{
		variants = 3;
	}},
	
	sandblock = new StaticBlock("sandblock"){{
		solid = true;
		variants = 3;
	}},
	
	snowblock = new StaticBlock("snowblock"){{
		solid = true;
		variants = 3;
	}},
	
	stoneblock = new StaticBlock("stoneblock"){{
		solid = true;
		variants = 3;
	}},
	
	blackstoneblock = new StaticBlock("blackstoneblock"){{
		solid = true;
		variants = 3;
	}},
	
	grassblock = new StaticBlock("grassblock"){{
		solid = true;
		variants = 2;
	}},
					
	mossblock = new StaticBlock("mossblock"){{
		solid = true;
	}},
	
	shrub = new Rock("shrub"){

	},
	
	rock = new Rock("rock"){{
		variants = 2;
		varyShadow = true;
		drops = new ItemStack(Item.stone, 3);
	}},
	
	icerock = new Rock("icerock"){{
		variants = 2;
		varyShadow = true;
		drops = new ItemStack(Item.stone, 3);
	}},
	
	blackrock = new Rock("blackrock"){{
		variants = 1;
		varyShadow = true;
		drops = new ItemStack(Item.stone, 3);
	}},
	
	dirtblock = new StaticBlock("dirtblock"){{
		solid = true;
	}},
	ship1floor1 = new StaticBlock("ship1-floor1"){{
		solid = true;
		variants = 0;
	}};
}
