package net.bestia.zoneserver.ecs.component;

import net.bestia.zoneserver.manager.PlayerBestiaEntityProxy;
import com.artemis.Component;

/**
 * Entity contains a {@link PlayerBestiaEntityProxy}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerBestia extends Component {

	public PlayerBestiaEntityProxy playerBestiaManager;
	
	public PlayerBestia() {
		
	}
	
	public PlayerBestia(PlayerBestiaEntityProxy manager) {
		this.playerBestiaManager = manager;
	}

}
