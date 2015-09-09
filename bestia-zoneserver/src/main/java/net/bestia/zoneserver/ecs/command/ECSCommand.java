package net.bestia.zoneserver.ecs.command;

import net.bestia.zoneserver.command.Command;

import com.artemis.Entity;
import com.artemis.World;

public abstract class ECSCommand extends Command {

	protected World world;
	protected Entity player;
	
	public void setWorld(World world) {
		this.world = world;
	}

	public void setPlayer(Entity player) {
		this.player = player;
	}
}