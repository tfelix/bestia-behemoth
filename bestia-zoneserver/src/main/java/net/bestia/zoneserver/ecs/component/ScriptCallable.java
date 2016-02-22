package net.bestia.zoneserver.ecs.component;

import java.util.concurrent.Callable;

import com.artemis.Component;

/**
 * Holds a callable function.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptCallable extends Component {
	
	public Callable<Void> fn;

}
