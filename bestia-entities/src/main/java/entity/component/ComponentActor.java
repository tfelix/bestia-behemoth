package net.bestia.entity.component;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

@Documented
@Retention(RUNTIME)
/**
 * Added to a component this annotation will automatically start a Component
 * managing actor upon installing this component and automatically remove the
 * actor if the component is deleted from the entity.
 * 
 * @author Thomas Felix
 *
 */
public @interface ComponentActor {

	/**
	 * Fully qualified actor name to get spawned if this component is
	 * installed.
	 */
	String value();

	/**
	 * This flag determines if an instanced actor will actively updates
	 * that an component has changed via a ComponentUpdateMessage which holds
	 * the component id which has changed.
	 */
	boolean updateActorOnChange() default false;
}
