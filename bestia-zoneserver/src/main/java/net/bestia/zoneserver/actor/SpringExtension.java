package net.bestia.zoneserver.actor;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import akka.actor.AbstractExtensionId;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * An Akka extension to provide access to the Spring manages Actor Beans.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class SpringExtension extends AbstractExtensionId<SpringExtension.SpringExt> {

	private static final Logger LOG = LoggerFactory.getLogger(SpringExtension.class);

	/**
	 * The identifier used to access the SpringExtension.
	 */
	public static final SpringExtension PROVIDER = new SpringExtension();

	/**
	 * Is used by Akka to instantiate the Extension identified by this
	 * ExtensionId, internal use only.
	 */
	@Override
	public SpringExt createExtension(ExtendedActorSystem system) {
		return new SpringExt();
	}

	/**
	 * The extension implementation.
	 */
	public static class SpringExt implements Extension {
		private volatile ApplicationContext applicationContext;

		/**
		 * Used to initialize the Spring application context for the extension.
		 * 
		 * @param applicationContext
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
			return Props.create(SpringActorProducer.class,
					applicationContext, actorBeanClass).withDeploy(Deploy.local());
		}

		/**
		 * Same as {@link #props(Class)} but inside the args can be additional
		 * arguments for the constructor of the {@link Actor}.
		 * 
		 * @param actorBeanClass
		 * @param args
		 *            Additional arguments for the actor ctor.
		 * @return
		 */
		public Props props(Class<? extends Actor> actorBeanClass, Object... args) {
			return Props.create(SpringActorProducer.class,
					applicationContext, actorBeanClass, args).withDeploy(Deploy.local());
		}
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
	public static Props getSpringProps(ActorSystem system, Class<? extends UntypedActor> clazz, Object... args) {

		final SpringExt springExt = SpringExtension.PROVIDER.get(system);
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
	public static Props getSpringProps(ActorSystem system, Class<? extends UntypedActor> clazz) {

		final SpringExt springExt = SpringExtension.PROVIDER.get(system);
		final Props props = springExt.props(clazz);
		return props;
	}

	public static ActorRef actorOf(ActorSystem system, Class<? extends UntypedActor> clazz) {

		// Try to create the class with the name field.
		try {
			final Field f = clazz.getField("NAME");
			final Class<?> t = f.getType();
			if (t == String.class) {
				return actorOf(system, clazz, (String) f.get(null));
			}
		} catch (Exception e) {
			// no op.
		}

		return actorOf(system, clazz, null);
	}

	public static ActorRef actorOf(ActorSystem system, Class<? extends UntypedActor> clazz, String name) {

		final Props props = getSpringProps(system, clazz);
		
		LOG.debug("Creating actor: {}.", clazz);

		if (name == null) {
			return system.actorOf(props);
		} else {
			return system.actorOf(props, name);
		}
	}
}
