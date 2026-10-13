package com.github.alexthe666.rats.server.entity.ai.goal;

import com.github.alexthe666.rats.server.entity.monster.Pirat;
import com.github.alexthe666.rats.server.misc.RatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class PiratWanderGoal extends Goal {
	private final Pirat rat;
	private double xPosition;
	private double yPosition;
	private double zPosition;
	private final double speed;
	private final int executionChance;
	private boolean mustUpdate;

	public PiratWanderGoal(Pirat pirat, double speedModifer) {
		this(pirat, speedModifer, 20);
	}

	public PiratWanderGoal(Pirat pirat, double speedModifer, int chance) {
		this.rat = pirat;
		this.speed = speedModifer;
		this.executionChance = chance;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.mustUpdate) {
			if (this.rat.getRandom().nextInt(executionChance) != 0) {
				return false;
			}
		}
		Vec3 vec3d = null;
		// Bounded loaded-only sampling; no water means no aquatic task.
		for (int attempt = 0; attempt < 64; attempt++) {
			BlockPos candidate = this.rat.blockPosition().offset(this.rat.getRandom().nextInt(21) - 10, this.rat.getRandom().nextInt(11) - 5, this.rat.getRandom().nextInt(21) - 10);
			if (this.rat.level().hasChunkAt(candidate) && this.rat.level().getFluidState(candidate).is(net.minecraft.tags.FluidTags.WATER)) { vec3d = candidate.getCenter(); break; }
		}
		if (vec3d == null) {
			return false;
		} else {
			BlockPos water = RatUtils.findLowestWater(BlockPos.containing(vec3d), this.rat);
			this.xPosition = water.getX();
			this.yPosition = water.getY();
			this.zPosition = water.getZ();
			this.mustUpdate = false;
			return true;
		}
	}

	@Override
	public boolean canContinueToUse() {
		return !this.rat.getNavigation().isDone();
	}

	@Override
	public void start() {
		this.rat.getNavigation().moveTo(this.xPosition, this.yPosition, this.zPosition, this.speed);
	}
}
