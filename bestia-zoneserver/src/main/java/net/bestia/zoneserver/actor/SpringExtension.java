package net.bestia.zoneserver.actor;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import akka.actor.AbstractExtensionId;
import akka.actor.Actor;
import akka.actor.Deploy;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.Props;

/**
 * An Akka extension to provide access to the Spring manages Actor Beans.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class SpringExtension extends AbstractExtensionId<SpringExtension.SpringExt> {
	/**
	 * The identifier used to access the SpringExtension.
	 */
	public static final SpringExtension SpringExtProvider = new SpringExtension();

	/**
	 * Is used by Akka to instantiate the Extension identified by this
	 * ExtensionId, internal use only.
	 */
	@Override
	public SpringExt createExtension(ExtendedActorSystem system) {
		return new SpringExt();
	}

	/**
	 * The Extension implementation.
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
	}
}
