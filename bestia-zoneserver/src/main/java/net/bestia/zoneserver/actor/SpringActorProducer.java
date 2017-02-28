package net.bestia.zoneserver.actor;

import java.util.Objects;

import org.springframework.context.ApplicationContext;

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;

/**
 * An actor producer that lets Spring create the Akka Actor instances.
 */
public class SpringActorProducer implements IndirectActorProducer {

	private final ApplicationContext applicationContext;
	private final Class<? extends Actor> actorBeanClass;
	private final Object[] args;

	public SpringActorProducer(ApplicationContext applicationContext, Class<? extends Actor> actorBeanClass) {
		
		this.applicationContext = Objects.requireNonNull(applicationContext);
		this.actorBeanClass = Objects.requireNonNull(actorBeanClass);
		this.args = null;
	}
	
	public SpringActorProducer(ApplicationContext applicationContext, 
			Class<? extends Actor> actorBeanClass,
			Object... args) {
		this.applicationContext = applicationContext;
		this.actorBeanClass = actorBeanClass;
		this.args = args;
	}

	@Override
	public Actor produce() {
		if(args == null) {
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
