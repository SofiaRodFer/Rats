package com.github.alexthe666.rats.server.misc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.BitSet;

/** Fixed 16-column seed indices; water doubles as the lantern's footprint. */
public final class FarmLayout {
	public final int width,height;
	public final BitSet water=new BitSet(256),lights=new BitSet(256);
	public FarmLayout(int width,int height){
		this.width=Math.max(1,Math.min(16,width));this.height=Math.max(1,Math.min(16,height));var uncovered=new BitSet(256);
		for(int z=0;z<this.height;z++)for(int x=0;x<this.width;x++)uncovered.set(z*16+x);
		while(!uncovered.isEmpty()){int best=-1,score=-1;for(int z=0;z<this.height;z++)for(int x=0;x<this.width;x++){int cell=z*16+x;if(this.water.get(cell))continue;int n=0;for(int i=uncovered.nextSetBit(0);i>=0;i=uncovered.nextSetBit(i+1))if(covered(x,z,i%16,i/16))n++;if(n>score){score=n;best=cell;}}if(best<0)break;this.water.set(best);this.lights.set(best);for(int i=uncovered.nextSetBit(0);i>=0;i=uncovered.nextSetBit(i+1))if(covered(best%16,best/16,i%16,i/16))uncovered.clear(i);}
	}
	private static boolean covered(int x,int z,int xx,int zz){return Math.max(Math.abs(x-xx),Math.abs(z-zz))<=4&&Math.abs(x-xx)+Math.abs(z-zz)<=6;}
	public boolean contains(int i){return i>=0&&i<256&&i%16<this.width&&i/16<this.height;}
	public boolean cropCell(int i){return this.contains(i)&&!this.water.get(i);}
	public BlockPos ground(BlockPos station,Direction direction,int i){return station.relative(direction,i/16+1).relative(direction.getClockWise(),i%16-this.width/2).below();}
}
