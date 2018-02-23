package net.bestia.zoneserver.configuration;

import net.bestia.entity.component.Component;
import net.bestia.entity.component.interceptor.BaseComponentInterceptor;
import net.bestia.entity.component.interceptor.Interceptor;
import bestia.messages.MessageApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
	 *
	 */
	@Bean
	public Interceptor defaultInterceptor(
			List<BaseComponentInterceptor<? extends Component>> interceptors,
			MessageApi msgApi) {

		final InterceptorComposite interceptor = new InterceptorComposite(interceptors);
		interceptor.addDefaultInterceptor(new ClientComponentSyncInterceptor(msgApi));
		interceptor.addDefaultInterceptor(new ActorUpdateComponentInterceptor(msgApi));

		return interceptor;
	}
}
