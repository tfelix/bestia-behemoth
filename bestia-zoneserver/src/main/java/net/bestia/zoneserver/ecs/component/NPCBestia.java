package net.bestia.zoneserver.ecs.component;

import net.bestia.zoneserver.manager.NPCBestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import com.artemis.Component;

/**
 * Entity contains a {@link PlayerBestiaManager}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class NPCBestia extends Component {

	public NPCBestiaManager manager;
	
	public NPCBestia() {
		
	}
	
	public NPCBestia(NPCBestiaManager manager) {
		this.manager = manager;
	}

}
