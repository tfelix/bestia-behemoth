package net.bestia.zoneserver.ecs.component;

import java.util.HashSet;
import java.util.Set;

import com.artemis.Component;

/**
 * This script is triggered when an entity touches/collides with it.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Script extends Component {

	/**
	 * The script to be triggered when touched.
	 */
	public String script;

	/**
	 * List of entities which have already triggered this script (and are
	 * currently inside the area the script is affecting).
	 */
	public final Set<Integer> lastTriggeredEntities = new HashSet<>();
}
