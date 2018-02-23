package net.bestia.entity.component;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

@Documented
@Retention(RUNTIME)
/**
 * This annotation added to a Component will tell the ClientComponentSyncInterceptor to
 * send this towards the client or all other users in sight if the component has
 * changed.
 * 
 * @author Thomas Felix
 *
 */
public @interface ComponentSync {

	SyncType value() default SyncType.OWNER;
}
