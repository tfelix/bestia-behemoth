package net.bestia.zoneserver.command.ecs;

import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;
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
	 * Returns the {@link PlayerEntityProxy} of the current player in the
	 * scope in whose context this command is executed.
	 * 
	 * @return The {@link PlayerEntityProxy} of the player currently
	 *         responsible for triggering this command.
	 */
	protected PlayerEntityProxy getPlayerBestiaProxy() {
		final ComponentMapper<PlayerBestia> playerMapper = world.getMapper(PlayerBestia.class);
		final PlayerEntityProxy pbm = playerMapper.get(player).playerBestia;
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