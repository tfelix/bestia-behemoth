package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.zoneserver.proxy.NpcBestiaEntityProxy;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

/**
 * Entity contains a {@link PlayerBestiaEntityProxy}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class NPCBestia extends Component {

	public NpcBestiaEntityProxy manager;
	
	public NPCBestia() {
		
	}
	
	public NPCBestia(NpcBestiaEntityProxy manager) {
		this.manager = manager;
	}

}
