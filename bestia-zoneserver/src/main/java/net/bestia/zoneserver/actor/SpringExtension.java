package net.bestia.zoneserver.actor;

import java.lang.reflect.Field;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.AbstractExtensionId;
import akka.actor.Actor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.Props;

/**
 * An Akka extension to provide access to the Spring manages Actor Beans.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class SpringExtension extends AbstractExtensionId<SpringExtension.SpringAkkaExt> {

	/**
	 * The identifier used to access the SpringExtension.
	 */
	public static final SpringExtension PROVIDER = new SpringExtension();

	private SpringExtension() {
		// no op.
	}

	/**
	 * Is used by Akka to instantiate the Extension identified by this
	 * ExtensionId, internal use only.
	 */
	@Override
	public SpringAkkaExt createExtension(ExtendedActorSystem system) {
		return new SpringAkkaExt();
	}

	/**
	 * The extension implementation.
	 */
	public static class SpringAkkaExt implements Extension {

		private volatile ApplicationContext applicationContext;

		private SpringAkkaExt() {
			// no op.
		}

		/**
		 * Used to initialize the Spring application context for the extension.
		 * 
		 * @param applicationContext
		 *            The Spring application context.
		 */
		public void initialize(ApplicationContext applicationContext) {
			this.applicationContext = applicationContext;
		}

		/**
		 * Create a Props for the specified actorBeanName using the
		 * SpringActorProducer class.
		 *
		 * @param actorBeanName
		 *            The name of the actor bean to create Props for.
		 * @return a Props that will create the named actor bean using Spring.
		 */
		public Props props(Class<? extends Actor> actorBeanClass) {
			return Props.create(
					SpringActorProducer.class,
					applicationContext,
					actorBeanClass)
					.withDeploy(Deploy.local());
		}

		/**
		 * Same as {@link #props(Class)} but inside the args can be additional
		 * arguments for the constructor of the {@link Actor}.
		 * 
		 * @param actorBeanClass
		 * @param args
		 *            Additional arguments for the actor ctor.
		 * @return A props object containing an application context.
		 */
		public Props props(Class<? extends Actor> actorBeanClass, Object... args) {
			return Props.create(
					SpringActorProducer.class,
					applicationContext,
					actorBeanClass,
					args)
					.withDeploy(Deploy.local());
		}
	}

	private static String getActorName(Class<? extends AbstractActor> clazz) {
		try {
			final Field f = clazz.getField("NAME");
			final Class<?> t = f.getType();
			if (t == String.class) {
				return (String) f.get(null);
			}

			return null;
		} catch (Exception e) {
			return null;
		}
	}

	public static ActorRef actorOf(ActorSystem system, Class<? extends AbstractActor> clazz) {

		final String actorName = getActorName(clazz);
		return actorOf(system, clazz, actorName);
	}

	/**
	 * Creates a new actor via injection of spring dependencies. Does give the
	 * actor a custom name by the user.
	 * 
	 * @param system
	 * @param clazz
	 * @param actorName
	 *            Default actor name, if the name is null the class name or a
	 *            random name is used.
	 * @return The created {@link ActorRef}.
	 */
	public static ActorRef actorOf(ActorSystem system, Class<? extends AbstractActor> clazz, String actorName) {

		final Props props = getSpringProps(system, clazz);
		return (actorName == null) ? system.actorOf(props) : system.actorOf(props, actorName);
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
	public static ActorRef actorOf(ActorContext ctx, Class<? extends AbstractActor> clazz) {
		
		final String actorName = getActorName(clazz);
		return actorOf(ctx, clazz, actorName);
	}

	/**
	 * Creates a new actor and already register it with this routing actor so it
	 * is considered when receiving messages.
	 * 
	 * @param context
	 *            The {@link ActorContext} under which the actor should be
	 *            spawned.
	 * @param clazz
	 *            The class of the {@link UntypedActor} to instantiate.
	 * @param name
	 *            The name under which the actor should be created.
	 * @return The created and already registered new actor.
	 */
	public static ActorRef actorOf(ActorContext context, Class<? extends AbstractActor> clazz, String name) {

		final Props props = getSpringProps(context.system(), clazz);
		return (name == null) ? context.actorOf(props) : context.actorOf(props, name);
	}

	/**
	 * Returns a new actor with a external name like
	 * {@link #actorOf(ActorContext, Class, String)} but with optional parameter
	 * arguments.
	 * 
	 * @param context
	 *            The {@link ActorContext} under which the actor should be
	 *            spawned.
	 * @param clazz
	 *            The class of the {@link AbstractActor} to be instantiated.
	 * @param name
	 *            The name under which the actor should be created.
	 * @param args
	 *            The additional arguments delivered to the actor.
	 * @return
	 */
	public static ActorRef actorOf(ActorContext context,
			Class<? extends AbstractActor> clazz,
			String name,
			Object... args) {

		final Props props = getSpringProps(context.system(), clazz, args);
		return (name == null) ? context.actorOf(props) : context.actorOf(props, name);
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
	public static ActorRef unnamedActorOf(ActorSystem system, Class<? extends AbstractActor> clazz) {

		final Props props = getSpringProps(system, clazz);
		return system.actorOf(props);
	}

	/**
	 * Alias for {@link #unnamedActorOf(ActorSystem, Class)} but allows
	 * additional arguments to be fed into the constructor.
	 * 
	 * @param system
	 * @param clazz
	 * @param args
	 * @return
	 */
	public static ActorRef unnamedActorOf(ActorContext ctx, Class<? extends AbstractActor> clazz, Object... args) {

		final Props props = getSpringProps(ctx.system(), clazz, args);
		return ctx.actorOf(props);
	}

	/**
	 * Small helper method to get props via the spring extension (and thus can
	 * use dependency injection).
	 * 
	 * @param clazz
	 *            The Actor class to get the props object for.
	 * @param args
	 *            The arguments are used by spring to fill in additional ctor
	 *            arguments.
	 * @return The created props object.
	 */
	public static Props getSpringProps(ActorSystem system, Class<? extends AbstractActor> clazz, Object... args) {

		return PROVIDER.get(system).props(clazz, args);
	}

	/**
	 * Small helper method to get props via the spring extension (and thus can
	 * use dependency injection).
	 * 
	 * @param clazz
	 *            The Actor class to get the props object for.
	 * @return The created props object.
	 */
	private static Props getSpringProps(ActorSystem system, Class<? extends AbstractActor> clazz) {

		return PROVIDER.get(system).props(clazz);
	}
}
