package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.artemis.Component;

import net.bestia.zoneserver.proxy.BestiaEntityProxy;
import net.bestia.zoneserver.script.InteractCallback;

/**
 * This allows the user to interact with the entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptInteract extends Component implements Serializable, InteractCallback {


	private static final long serialVersionUID = 1L;
	
	private List<InteractCallback> callbacks = new ArrayList<>();

	/**
	 * Clears all stored callbacks.
	 */
	public void clear() {
		callbacks.clear();
	}

	/**
	 * Adds an callback.
	 * 
	 * @param fn
	 *            The callback to be added.
	 */
	public void addCallback(InteractCallback fn) {
		callbacks.add(fn);
	}

	@Override
	public void call(BestiaEntityProxy owner, BestiaEntityProxy caller) {
		callbacks.forEach(x -> {
			try {
				x.call(owner, caller);
			} catch (Exception ex) {
				// Could not execute callback.
				// no op.
			}
		});
	}

}
