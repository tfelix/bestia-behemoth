package net.bestia.zoneserver.actor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;

/**
 * An actor producer that lets Spring create the Akka Actor instances.
 */
class SpringActorProducer implements IndirectActorProducer {

	private static final Logger LOG = LoggerFactory.getLogger(SpringActorProducer.class);

	private final ApplicationContext applicationContext;
	private final Class<? extends Actor> actorBeanClass;
	private final Object[] args;

	public SpringActorProducer(ApplicationContext applicationContext, Class<? extends Actor> actorBeanClass) {
		this(applicationContext, actorBeanClass, new Object[] {});

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
		if (args.length == 0) {
			return (Actor) applicationContext.getBean(actorBeanClass);
		} else {

			try {
				Object[] ctorArgs = getAllCtorArgs();
				return (Actor) applicationContext.getBean(actorBeanClass, ctorArgs);
			} catch (ClassNotFoundException e) {
				throw new BeanCreationException("Class for mixed argument list not found.", e);
			}
		}
	}

	/**
	 * This will create all arguments since we need all ctor arguments present
	 * inside the array so spring can create the bean. We will combine the
	 * arguments provided by the user and the ones needed by the ctor.
	 * 
	 * @return All arguments for the bean ctor invocation.
	 */
	private Object[] getAllCtorArgs() throws ClassNotFoundException {

		Constructor<?>[] ctors = actorBeanClass.getConstructors();
		Constructor<?> autoCtor = null;

		// Create a hashset of all provided arg objects.
		final Set<Class<?>> availableArgsClasses = Stream.of(args).map(t -> t.getClass()).collect(Collectors.toSet());

		for (Constructor<?> ctor : ctors) {
			if (ctor.isAnnotationPresent(Autowired.class)) {
				autoCtor = ctor;
				break;
			}
		}

		if (autoCtor == null) {
			LOG.warn("No Ctor with Autowire annotation found. Can not create bean: {}", actorBeanClass.getName());
			return null;
		}

		final Class<?>[] params = autoCtor.getParameterTypes();
		// We need to use in order to get the boxed type of primitive
		// arguments.
		final Set<Class<?>> neededArgs = new HashSet<>();
		
		for (Class<?> clazz : Arrays.asList(params)) {
			Optional<Class<?>> primClazz = getClassFromPrimitive(clazz);
			
			if(primClazz.isPresent()) {
				neededArgs.add(primClazz.get());
			} else {
				neededArgs.add(clazz);
			}
		
		}

		// Remove all available arg classes.
		availableArgsClasses.forEach(neededArgs::remove);

		// Try to create the missing args via spring beans invocation.
		final List<Object> availableArgs = new ArrayList<>(Arrays.asList(args));
		for (Class<?> clazz : neededArgs) {
			final Object argObj = applicationContext.getBean(clazz);
			availableArgs.add(argObj);
		}

		return availableArgs.toArray();
	}
	
	private Optional<Class<?>> getClassFromPrimitive(Class<?> clazz) {
		if(clazz == long.class) {
			return Optional.of(Long.class);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public Class<? extends Actor> actorClass() {
		return actorBeanClass;
	}
}
