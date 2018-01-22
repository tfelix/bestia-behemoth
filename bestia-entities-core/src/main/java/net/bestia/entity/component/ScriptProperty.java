package net.bestia.entity.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Methods of properties annotated with this will be made available
 * for editing via a PropertySetter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
public @interface ScriptProperty {

	enum Accessor { GETTER, SETTER, NONE }

	/**
	 * This maps the value to this name. If the property
	 * if of a basic type it will made accessible. If a
	 * non basic type is annotated all its public member
	 * are made available.
	 */
	String value() default "";

	/**
	 * If the type of method could not be detected via its
	 * name then the accesor is needed to specifiy it.
	 * @return
	 */
	Accessor accessor() default Accessor.NONE;
}
