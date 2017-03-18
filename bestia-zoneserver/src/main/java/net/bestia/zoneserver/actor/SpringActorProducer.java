package net.bestia.zoneserver.actor;

import java.util.Objects;

import org.springframework.context.ApplicationContext;

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;

/**
 * An actor producer that lets Spring create the Akka Actor instances.
 */
 class SpringActorProducer implements IndirectActorProducer {

	private final ApplicationContext applicationContext;
	private final Class<? extends Actor> actorBeanClass;
	private final Object[] args;

	public SpringActorProducer(ApplicationContext applicationContext, Class<? extends Actor> actorBeanClass) {
		this(applicationContext, actorBeanClass, new Object[]{});
		
		// no op.
	}
	
	public SpringActorProducer(ApplicationContext applicationContext, 
			Class<? extends Actor> actorBeanClass,
			Object... args) {
		
		this.applicationContext = Objects.requireNonNull(applicationContext);
		this.actorBeanClass = Objects.requireNonNull(actorBeanClass);
		this.args = Objects.requireNonNull(args);
	}

	@Override
	public Actor produce() {
		if(args.length == 0) {
			return (Actor) applicationContext.getBean(actorBeanClass);
		} else {
			return (Actor) applicationContext.getBean(actorBeanClass, args);
		}
	}

	@Override
	public Class<? extends Actor> actorClass() {
		return actorBeanClass;
	}
}
