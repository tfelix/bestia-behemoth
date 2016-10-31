package net.bestia.zoneserver.actor;

import java.lang.reflect.Field;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;

/**
 * Should be the base class for the whole akka system. This class provides some
 * helper methods to simply create dependency injected actors via spring.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class BestiaActor extends UntypedActor {

	public BestiaActor() {
		super();
	}

	/**
	 * Creates a new actor and already register it with this routing actor so it
	 * is considered when receiving messages.
	 * 
	 * @param clazz
	 *            The class of the {@link UntypedActor} to instantiate.
	 * @param name
	 *            The name under which the actor should be created.
	 * @return The created and already registered new actor.
	 */
	protected ActorRef createActor(Class<? extends UntypedActor> clazz, String name) {

		final Props props = getSpringProps(clazz);
		final ActorRef newActor = getContext().actorOf(props, name);
		return newActor;
	}

	/**
	 * Like {@link #createActor(Class, String)} but it will examine the given
	 * class if it has a static public string field called NAME and will use
	 * this name as actor name. If no such field exists the name "NONAME" will
	 * be used.
	 * 
	 * @param clazz
	 *            The class of the {@link UntypedActor} to instantiate.
	 * @return The created and already registered new actor.
	 */
	protected ActorRef createActor(Class<? extends UntypedActor> clazz) {

		final Props props = getSpringProps(clazz);

		try {
			final Field f = clazz.getField("NAME");
			final Class<?> t = f.getType();
			if (t == String.class) {
				return getContext().actorOf(props, (String) f.get(null));
			}
		} catch (Exception e) {
			// no op.
		}
		
		return getContext().actorOf(props, "NONAME");
	}

	/**
	 * Small helper method to get props via the spring extension (and thus can
	 * use dependency injection).
	 * 
	 * @param clazz
	 *            The Actor class to get the props object for.
	 * @return The created props object.
	 */
	protected Props getSpringProps(Class<? extends UntypedActor> clazz) {

		final SpringExt springExt = SpringExtension.Provider.get(getContext().system());
		final Props props = springExt.props(clazz);
		return props;
	}

}