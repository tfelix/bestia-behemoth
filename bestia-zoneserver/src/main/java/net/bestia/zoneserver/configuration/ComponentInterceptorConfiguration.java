package net.bestia.zoneserver.configuration;

import net.bestia.entity.component.interceptor.ActorUpdateComponentInterceptor;
import net.bestia.entity.component.interceptor.Interceptor;
import net.bestia.entity.component.interceptor.InterceptorComposite;
import net.bestia.messages.MessageApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Configuration for the component interceptor.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class ComponentInterceptorConfiguration {

	/**
	 * Adds a new default interceptor to the interceptor class.
	 */
	@Bean
	public Interceptor defaultInterceptor(
			// List<BaseComponentInterceptor<? extends Component>> interceptors,
			MessageApi msgApi) {

		final InterceptorComposite interceptor = new InterceptorComposite(Collections.emptyList());
		interceptor.addDefaultInterceptor(new ActorUpdateComponentInterceptor(msgApi));

		return interceptor;
	}
}
