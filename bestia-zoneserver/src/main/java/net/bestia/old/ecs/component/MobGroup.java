package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;

import com.artemis.Component;

/**
 * Holds the name of the mob group. This will be used determine to which group a
 * mob bestia belongs.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MobGroup extends Component implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public String groupName;
}
