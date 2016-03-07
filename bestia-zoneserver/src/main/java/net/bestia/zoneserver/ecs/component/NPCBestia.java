package net.bestia.zoneserver.ecs.component;

import net.bestia.zoneserver.manager.NpcBestiaEntityProxy;
import net.bestia.zoneserver.manager.PlayerBestiaEntityProxy;
import com.artemis.Component;

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
