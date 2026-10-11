package com.github.alexthe666.rats.server.entity.ai.goal;

import com.github.alexthe666.rats.server.entity.rat.RatCommand;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.misc.CageHabitat;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RatReturnToCageGoal extends Goal {
	private final TamedRat rat;
	private GlobalPos home;
	private int expires;
	public RatReturnToCageGoal(TamedRat rat) { this.rat = rat; this.setFlags(EnumSet.of(Flag.MOVE)); }
	@Override
	public boolean canUse() {
		if (this.rat.tickCount < this.rat.cageReturnAfter || this.rat.getCommand() != RatCommand.WANDER || !this.rat.canMove() || this.rat.isPassenger() || this.rat.isInCage() || this.rat.isInTube() || this.rat.getCageHome().isEmpty()) return false;
		this.rat.cageReturnAfter = this.rat.tickCount + 200;
		this.home = this.rat.getCageHome().get();
		return this.home.dimension().equals(this.rat.level().dimension()) && CageHabitat.discover(this.rat.level(), this.home.pos()).hasRoom(this.rat.level());
	}
	@Override
	public void start() { this.expires = this.rat.tickCount + 400; }
	@Override
	public boolean canContinueToUse() { return this.rat.tickCount < this.expires && this.rat.canMove() && this.rat.getCommand() == RatCommand.WANDER && this.rat.getCageHome().filter(this.home::equals).isPresent() && !this.rat.isInCage() && !this.rat.isInTube() && this.rat.level().hasChunkAt(this.home.pos()); }
	@Override
	public void stop() { this.rat.getNavigation().stop(); this.rat.cageReturnAfter = this.rat.tickCount + 600; }
	@Override
	public void tick() {
		Vec3 entry = Vec3.atCenterOf(this.home.pos());
		if (this.rat.distanceToSqr(entry) < 4) {
			CageHabitat.discover(this.rat.level(), this.home.pos()).admit(this.rat, this.home.pos());
			this.expires = 0;
		} else if (this.rat.tickCount % 20 == 0) {
			Vec3 delta = entry.subtract(this.rat.position());
			Vec3 waypoint = delta.lengthSqr() > 100 ? this.rat.position().add(delta.normalize().scale(10)) : entry;
			this.rat.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.1);
		}
	}
}
