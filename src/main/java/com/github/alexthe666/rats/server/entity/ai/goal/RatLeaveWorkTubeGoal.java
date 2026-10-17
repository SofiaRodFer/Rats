package com.github.alexthe666.rats.server.entity.ai.goal;

import com.github.alexthe666.rats.server.block.RatCageBlock;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.TubeTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;

/** Recover workers caught by old tube routing or a command change, without teleporting. */
public final class RatLeaveWorkTubeGoal extends Goal {
    private final TamedRat rat;
    private final TubeTravel travel = new TubeTravel();
    private BlockPos exit;
    private int searchAfter, expires;

    public RatLeaveWorkTubeGoal(TamedRat rat) {
        this.rat = rat;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }
    private boolean recovering() {
        return this.rat.canMove() && !this.rat.isPassenger() && this.rat.getCommand().workCommand
            && !TubeTravel.usesTransportTubes(this.rat);
    }
    @Override public boolean canUse() {
        if (!this.recovering() || !this.rat.isInTube() || this.rat.tickCount < this.searchAfter) return false;
        this.searchAfter = this.rat.tickCount + 40;
        // A cage-connected tube is part of its habitat, not a stranded worker.
        if (!com.github.alexthe666.rats.server.misc.CageHabitat.discover(this.rat.level(),this.rat.blockPosition()).cages().isEmpty()) return false;
        var queue = new ArrayDeque<BlockPos>(); var visited = new HashSet<BlockPos>();
        queue.add(this.rat.blockPosition()); visited.add(this.rat.blockPosition());
        while (!queue.isEmpty() && visited.size() <= 512) {
            var current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                var next = current.relative(direction);
                if (!this.rat.level().hasChunkAt(next) || !visited.add(next)) continue;
                if (TubeTravel.tube(this.rat.level(),next)) queue.addLast(next);
                else if (this.safeExit(next)) { this.exit = next; return true; }
            }
        }
        return false;
    }
    private boolean safeExit(BlockPos pos) {
        var level = this.rat.level(); var floor = level.getBlockState(pos.below()); var state = level.getBlockState(pos);
        return !TubeTravel.tube(level,pos) && !TubeTravel.dock(level,pos)
            && !(level.getBlockState(pos).getBlock() instanceof RatCageBlock)
            && level.getFluidState(pos).isEmpty() && TubeTravel.passable(level,pos)
            && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.WITHER_ROSE)
            && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.POWDER_SNOW)
            && !TubeTravel.tube(level,pos.below()) && !TubeTravel.dock(level,pos.below())
            && !floor.getCollisionShape(level,pos.below()).isEmpty()
            && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(Blocks.CAMPFIRE) && !floor.is(Blocks.SOUL_CAMPFIRE) && !floor.is(Blocks.CACTUS)
            && level.noCollision(this.rat,this.rat.getBoundingBox().move(TubeTravel.point(level,pos).subtract(this.rat.position())));
    }
    @Override public void start() { this.expires = this.rat.tickCount + 400; this.rat.getNavigation().stop(); }
    @Override public boolean canContinueToUse() {
        return this.exit != null && this.recovering() && this.rat.tickCount < this.expires && this.safeExit(this.exit)
            && this.rat.position().distanceToSqr(TubeTravel.point(this.rat.level(),this.exit)) >= .025;
    }
    @Override public void tick() { this.travel.move(this.rat,this.rat.blockPosition(),this.exit,-1); }
    @Override public void stop() {
        this.exit = null; this.travel.reset(); this.rat.getNavigation().stop();
        if (!this.rat.isInTube()) { this.rat.noPhysics = false; this.rat.setNoGravity(this.rat.isFlying()); }
    }
}
