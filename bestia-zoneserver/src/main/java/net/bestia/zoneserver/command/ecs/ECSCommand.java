package net.bestia.zoneserver.command.ecs;

import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;

public abstract class ECSCommand extends Command {

	protected Zone zone;
	protected World world;
	protected Map map;
	protected Entity player;

	public void setZone(Zone zone) {
		this.zone = zone;
	}
	
	public void setWorld(World world) {
		this.world = world;
	}

	public void setPlayer(Entity player) {
		this.player = player;
	}

	/**
	 * Returns the {@link PlayerBestiaEntityProxy} of the current player in the
	 * scope in whose context this command is executed.
	 * 
	 * @return The {@link PlayerBestiaEntityProxy} of the player currently
	 *         responsible for triggering this command.
	 */
	protected PlayerBestiaEntityProxy getPlayerBestiaManager() {
		final ComponentMapper<PlayerBestia> playerMapper = world.getMapper(PlayerBestia.class);
		final PlayerBestiaEntityProxy pbm = playerMapper.get(player).playerBestiaManager;
		return pbm;
	}

	/**
	 * Sets the map on which this command gets executed.
	 * 
	 * @param map
	 */
	public void setMap(Map map) {
		this.map = map;
	}
}