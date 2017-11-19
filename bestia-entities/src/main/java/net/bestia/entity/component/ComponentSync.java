/**
 * 
 */
package net.bestia.entity.component;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

@Documented
@Retention(RUNTIME)
/**
 * This annotation added to a Component will tell the DefaultSyncInterceptor to
 * send this towards the client or all other users in sight if the component has
 * changed.
 * 
 * @author Thomas Felix
 *
 */
public @interface ComponentSync {

	/**
	 * If only one sync type should be given this value can be used. However IF
	 * the types array is used the array will take precedence.
	 * 
	 * @return
	 */
	SyncType value() default SyncType.OWNER;

}
