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


	String value();
}
