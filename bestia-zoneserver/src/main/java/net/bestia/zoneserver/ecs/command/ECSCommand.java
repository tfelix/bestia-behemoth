package net.bestia.zoneserver.ecs.command;

import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManagerInterface;

import com.artemis.ComponentMapper;
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

	/**
	 * Returns the {@link PlayerBestiaManager} of the current player in the
	 * scope in whose context this command is executed.
	 * 
	 * @return The {@link PlayerBestiaManager} of the player currently
	 *         responsible for triggering this command.
	 */
	protected PlayerBestiaManagerInterface getPlayerBestiaManager() {
		final ComponentMapper<PlayerBestia> playerMapper = world
				.getMapper(PlayerBestia.class);
		final PlayerBestiaManagerInterface pbm = playerMapper.get(player).playerBestiaManager;
		return pbm;
	}
}