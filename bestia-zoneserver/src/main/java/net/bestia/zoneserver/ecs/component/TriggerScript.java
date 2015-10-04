package net.bestia.zoneserver.ecs.component;

import java.util.HashSet;
import java.util.Set;

import net.bestia.zoneserver.script.temp.MapTriggerScript;

import com.artemis.Component;

/**
 * This script is triggered when an entity touches/collides with it.
 * @author Thomas
 *
 */
public class TriggerScript extends Component {
	
	/**
	 * The script to be triggered when touched.
	 */
	public MapTriggerScript script;	
	public Set<Integer> lastTriggeredEntities;
	
	public TriggerScript() {
		lastTriggeredEntities = new HashSet<>();
	}
	
}
