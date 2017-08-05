package net.bestia.entity.component;

import java.util.Objects;

import akka.actor.ActorRef;
import net.bestia.entity.component.Component;

/**
 * This modules do the heavy lifting in actor creation. They will translate the
 * component data into a the data an actor needs to start work.
 * 
 * @author Thomas Felix
 *
 */
public abstract class ActorComponentFactoryModule<T extends Component> {

	private final Class<T> type;

	public ActorComponentFactoryModule(Class<T> type) {

		this.type = Objects.requireNonNull(type);
	}

	public Class<? extends Component> buildActorFor() {
		return type;
	}

	public ActorRef buildActor(Component component) {
		if (component.getClass().isAssignableFrom(type)) {
			return doBuildActor(type.cast(component));
		} else {
			throw new IllegalArgumentException("Wrong component class. Not supported by this recycler.");
		}
	}

	/**
	 * Builds the actor which handles the component and initialized it with all
	 * the needed data.
	 * 
	 * @param component
	 * @return
	 */
	protected abstract ActorRef doBuildActor(T component);
}
