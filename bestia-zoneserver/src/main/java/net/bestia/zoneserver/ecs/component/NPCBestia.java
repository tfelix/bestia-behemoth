package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;

import com.artemis.Component;

import net.bestia.zoneserver.proxy.NpcEntityProxy;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;

/**
 * Entity contains a {@link PlayerEntityProxy}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class NPCBestia extends Component implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public NpcEntityProxy manager;
	
	public NPCBestia() {
		
	}
	
	public NPCBestia(NpcEntityProxy manager) {
		this.manager = manager;
	}

}
