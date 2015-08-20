package net.bestia.zoneserver.ecs.component;

import net.bestia.zoneserver.manager.PlayerBestiaManager;

import com.artemis.Component;

/**
 * Entity can be controlled by a player. The system periodically checks for input. With the reference to the
 * {@link PlayerBestiaManager} it is possible to get all outstanding control messages.
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
