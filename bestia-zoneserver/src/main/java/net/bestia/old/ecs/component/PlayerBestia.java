package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.zoneserver.proxy.PlayerEntityProxy;

/**
 * Entity contains a {@link PlayerEntityProxy}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerBestia extends Component {

	public PlayerEntityProxy playerBestia;
	
	public PlayerBestia() {
		
	}
	
	public PlayerBestia(PlayerEntityProxy playerBestia) {
		this.playerBestia = playerBestia;
	}

}
