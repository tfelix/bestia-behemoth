package net.bestia.zoneserver.ecs.component;

import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManagerInterface;

import com.artemis.Component;

/**
 * Entity contains a {@link PlayerBestiaManager}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerBestia extends Component {

	public PlayerBestiaManagerInterface playerBestiaManager;
	
	public PlayerBestia() {
		
	}
	
	public PlayerBestia(PlayerBestiaManagerInterface manager) {
		this.playerBestiaManager = manager;
	}

}
