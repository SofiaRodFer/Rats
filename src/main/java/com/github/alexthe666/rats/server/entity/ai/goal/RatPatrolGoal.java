package com.github.alexthe666.rats.server.entity.ai.goal;

import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class RatPatrolGoal extends Goal {

	private final TamedRat rat;
	private GlobalPos nextNode;
	private int nodeIndex = 0;
	private int expires, retryAt;

	public RatPatrolGoal(TamedRat rat) {
		this.rat = rat;
		this.setFlags(java.util.EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return this.rat.tickCount >= this.retryAt && this.rat.canMove() && !this.rat.isInCage() && !this.rat.isInTube() && this.rat.isPatrolCommand() && this.rat.getPatrolNodes().stream().anyMatch(pos -> pos.dimension().equals(this.rat.level().dimension()) && this.rat.level().hasChunkAt(pos.pos())) && (this.rat.getTarget() == null || !this.rat.getTarget().isAlive());
	}
	@Override public void start() { this.expires = this.rat.tickCount + 400; }
	@Override public boolean canContinueToUse() { return this.rat.tickCount < this.expires && this.canUse(); }
	@Override public void stop() { this.rat.getNavigation().stop(); this.nextNode = null; this.nodeIndex++; this.retryAt = this.rat.tickCount + 40; }

	public void tick() {
		if (this.rat.getPatrolNodes().isEmpty()) return;
		this.nodeIndex = Math.floorMod(this.nodeIndex, this.rat.getPatrolNodes().size());
		if (this.nextNode == null) {
			this.nextNode = this.rat.getPatrolNodes().get(this.nodeIndex);
		}
		if (!this.nextNode.dimension().equals(this.rat.level().dimension()) || !this.rat.level().hasChunkAt(this.nextNode.pos())) { this.nextNode = null; this.nodeIndex++; return; }

		if (this.nextNode != null && this.rat.distanceToSqr(Vec3.atCenterOf(this.nextNode.pos())) <= this.rat.getRatHarvestDistance(0.0D)) {
			++this.nodeIndex;
			this.nextNode = null;
			if (this.nodeIndex > this.rat.getPatrolNodes().size() - 1) {
				this.nodeIndex = 0;
			}

			try {
				this.nextNode = this.rat.getPatrolNodes().get(this.nodeIndex);
			} catch (Exception var2) {
				this.nodeIndex = 0;
			}
		} else if (this.nextNode != null && this.rat.tickCount % 20 == 0) {
			this.rat.getNavigation().moveTo((double) ((float) this.nextNode.pos().getX()) + 0.5D, this.nextNode.pos().getY() + 1, (double) ((float) this.nextNode.pos().getZ()) + 0.5D, 1.0D);
		}

	}
}
