package net.bestia.zoneserver.actor;

import java.lang.reflect.Field;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
import net.bestia.zoneserver.actor.zone.SendClientActor;

/**
 * Should be the base class for the whole akka system. This class provides some
 * helper methods to simply create dependency injected actors via spring.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public abstract class BestiaActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private ActorRef responder;

	public BestiaActor() {
		super();
	}

	/**
	 * This will deliver the given message back to the account. In order to do
	 * this a {@link SendClientActor} responder is used. The actor will be
	 * created when necessary (this means the method is first invoked).
	 * 
	 * @param msg
	 */
	protected void sendClient(JsonMessage msg) {
		LOG.debug(String.format("Sending to client: %s", msg.toString()));
		if (responder == null) {
			responder = createActor(SendClientActor.class);
		}

		responder.tell(msg, getSelf());
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

		// Try to create the class with the name field.
		try {
			final Field f = clazz.getField("NAME");
			final Class<?> t = f.getType();
			if (t == String.class) {
				return getContext().actorOf(props, (String) f.get(null));
			}
		} catch (Exception e) {
			// no op.
		}

		return getContext().actorOf(props);
	}

	/**
	 * Unlike {@link #createActor(Class)} this wont check the given class for a
	 * name and just assign a random name. This is important when a lot of
	 * actors are created and destroyed to avoid performance bottlenecks.
	 * 
	 * @param clazz
	 *            The class to create an actor from.
	 * @return The created and already registered new actor.
	 */
	protected ActorRef createUnnamedActor(Class<? extends UntypedActor> clazz) {
		final Props props = getSpringProps(clazz);
		return getContext().actorOf(props);
	}

	/**
	 * Small helper method to get props via the spring extension (and thus can
	 * use dependency injection).
	 * 
	 * @param clazz
	 *            The Actor class to get the props object for.
	 * @return The created props object.
	 */
	protected Props getSpringProps(Class<? extends UntypedActor> clazz, Object... args) {

		final SpringExt springExt = SpringExtension.PROVIDER.get(getContext().system());
		final Props props = springExt.props(clazz, args);
		return props;
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

		final SpringExt springExt = SpringExtension.PROVIDER.get(getContext().system());
		final Props props = springExt.props(clazz);
		return props;
	}

}