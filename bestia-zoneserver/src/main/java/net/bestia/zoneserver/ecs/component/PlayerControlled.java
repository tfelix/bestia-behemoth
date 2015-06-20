package net.bestia.zoneserver.ecs.component;

import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

import com.artemis.Component;

/**
 * Entity can be controlled by a player. The system periodically checks for
 * input.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerControlled extends Component {

	public final PlayerBestiaManager playerBestia;

	public PlayerControlled(PlayerBestiaManager playerBestiaManager) {
		this.playerBestia = playerBestiaManager;
	}

}
