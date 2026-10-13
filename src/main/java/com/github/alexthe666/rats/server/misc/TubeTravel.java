package com.github.alexthe666.rats.server.misc;
import com.github.alexthe666.rats.server.block.*;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Loaded-only weighted travel. Tubes preferred; walkable gaps allowed. */
public final class TubeTravel {
	private List<BlockPos> route=List.of(); private BlockPos destination; private int refresh;
	public static boolean tube(Level l,BlockPos p){return l.hasChunkAt(p)&&l.getBlockState(p).getBlock() instanceof RatTubeBlock;}
	public static boolean dock(Level l,BlockPos p){return l.hasChunkAt(p)&&l.getBlockState(p).getBlock() instanceof RatStorageBlock b&&b.kind==RatStorageBlock.Kind.CONTROLLER;}
	public static boolean passable(Level l,BlockPos p){if(!l.hasChunkAt(p)||l.isOutsideBuildHeight(p))return false;if(tube(l,p)||dock(l,p))return true;if(!l.getBlockState(p).getCollisionShape(l,p).isEmpty()||!l.getFluidState(p).isEmpty())return false;return !l.getBlockState(p.below()).getCollisionShape(l,p.below()).isEmpty()||tube(l,p.below())&&tube(l,p.above());}
	public static Vec3 point(Level l,BlockPos p){return new Vec3(p.getX()+.5,p.getY()+(tube(l,p)||dock(l,p)?.27:0),p.getZ()+.5);}
	private record Node(BlockPos pos,int cost){}
	public static BlockPos inventoryDock(Level l,BlockPos inventory,Direction preferred){if(tube(l,inventory.relative(preferred)))return inventory.relative(preferred);for(Direction d:Direction.values())if(tube(l,inventory.relative(d)))return inventory.relative(d);return inventory.relative(preferred);}
	public static List<BlockPos> route(Level l,BlockPos origin,BlockPos from,BlockPos to,int radius){
		if(tube(l,from)||dock(l,from)){var pure=new ArrayDeque<BlockPos>();var parents=new HashMap<BlockPos,BlockPos>();pure.add(from);parents.put(from,from);while(!pure.isEmpty()&&parents.size()<8192){BlockPos p=pure.removeFirst();if(p.equals(to)){var found=new ArrayList<BlockPos>();for(BlockPos v=to;!v.equals(from);v=parents.get(v))found.add(v);Collections.reverse(found);return found.isEmpty()?List.of(to):found;}for(Direction d:Direction.values()){BlockPos n=p.relative(d);if(origin.distSqr(n)<=radius*radius&&!parents.containsKey(n)&&(tube(l,n)||dock(l,n))){parents.put(n,p);pure.add(n);}}}}
		if(from.equals(to))return List.of(to);var q=new PriorityQueue<Node>(Comparator.comparingInt(Node::cost));var costs=new HashMap<BlockPos,Integer>();var prev=new HashMap<BlockPos,BlockPos>();q.add(new Node(from,0));costs.put(from,0);int count=0;
		while(!q.isEmpty()&&count++<8192){var n=q.remove();if(n.cost!=costs.get(n.pos))continue;if(n.pos.equals(to)){var result=new ArrayList<BlockPos>();for(BlockPos p=to;!p.equals(from);p=prev.get(p))result.add(p);Collections.reverse(result);return result;}for(Direction d:Direction.values()){BlockPos p=n.pos.relative(d);if(origin.distSqr(p)>radius*radius||!passable(l,p))continue;int cost=n.cost+(tube(l,p)||dock(l,p)?1:6);if(cost>=costs.getOrDefault(p,Integer.MAX_VALUE))continue;costs.put(p,cost);prev.put(p,n.pos);q.add(new Node(p,cost));}}
		return List.of();
	}
	public void reset(){this.route=List.of();this.destination=null;this.refresh=0;}
	public boolean move(TamedRat rat,BlockPos origin,BlockPos target,int radius){
		Vec3 end=point(rat.level(),target);if(rat.position().distanceToSqr(end)<.025){this.reset();return true;}
		if(!target.equals(this.destination)||rat.tickCount>=this.refresh||this.route.isEmpty()||!passable(rat.level(),this.route.get(0))){this.destination=target;this.refresh=rat.tickCount+40;this.route=new ArrayList<>(route(rat.level(),origin,rat.blockPosition(),target,radius));if(!this.route.isEmpty())this.route.add(0,rat.blockPosition());}
		if(this.route.isEmpty()){if(!tube(rat.level(),rat.blockPosition())&&!dock(rat.level(),rat.blockPosition()))rat.getNavigation().moveTo(end.x,end.y,end.z,1);return false;}
		rat.getNavigation().stop();rat.setDeltaMovement(Vec3.ZERO);BlockPos next=this.route.get(0);Vec3 delta=point(rat.level(),next).subtract(rat.position());if(delta.lengthSqr()<.015){this.route.remove(0);return false;}Vec3 step=delta.normalize().scale(Math.min(.14,delta.length()));boolean inside=tube(rat.level(),rat.blockPosition())||dock(rat.level(),rat.blockPosition()),entering=tube(rat.level(),next)||dock(rat.level(),next);
		if(!inside&&!entering&&!rat.level().noCollision(rat,rat.getBoundingBox().move(step))){this.reset();return false;}
		rat.noPhysics=inside;rat.setNoGravity(inside||entering);rat.fallDistance=0;rat.setYRot((float)(Math.atan2(delta.z,delta.x)*180/Math.PI-90));rat.setPos(rat.position().add(step));rat.recordWorkProgress();return false;
	}
}
