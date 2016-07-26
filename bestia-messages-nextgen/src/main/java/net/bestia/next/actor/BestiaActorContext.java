package net.bestia.next.actor;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * This is a bean which will be used to wire up all the internals and needed
 * classes inside the akka actors. This class ist the last one which will get
 * created by spring via injection and then fed into the actor system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaActorContext {

	private final ApplicationContext springContext;

	public BestiaActorContext(ApplicationContext springContext) {
		this.springContext = Objects.requireNonNull(springContext);
	}

	public ApplicationContext getSpringContext() {
		return springContext;
	}

}
