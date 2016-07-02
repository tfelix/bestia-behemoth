package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;
import java.util.concurrent.Callable;

import com.artemis.Component;

/**
 * Holds a callable function.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptCallable extends Component implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	public Callable<Void> fn;

}
