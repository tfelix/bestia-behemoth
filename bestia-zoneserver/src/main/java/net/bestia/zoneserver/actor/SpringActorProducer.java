package net.bestia.zoneserver.actor;

import org.springframework.context.ApplicationContext;

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;

/**
 * An actor producer that lets Spring create the Akka Actor instances.
 */
public class SpringActorProducer implements IndirectActorProducer {

	private final ApplicationContext applicationContext;
	private final Class<? extends Actor> actorBeanClass;

	public SpringActorProducer(ApplicationContext applicationContext, Class<? extends Actor> actorBeanClass) {
		this.applicationContext = applicationContext;
		this.actorBeanClass = actorBeanClass;
	}

	@Override
	public Actor produce() {
		return (Actor) applicationContext.getBean(actorBeanClass);
	}

	@Override
	public Class<? extends Actor> actorClass() {
		return actorBeanClass;
	}
}
