package net.bestia.zoneserver.configuration;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import net.bestia.entity.component.Component;
import net.bestia.entity.component.interceptor.BaseComponentInterceptor;
import net.bestia.entity.component.interceptor.DefaultSyncInterceptor;
import net.bestia.entity.component.interceptor.Interceptor;
import net.bestia.messages.MessageApi;

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
	 * @param interceptors
	 * @param msgApi
	 * @return
	 */
	@Primary
	@Bean
	public Interceptor defaultInterceptor(List<BaseComponentInterceptor<? extends Component>> interceptors,
			MessageApi msgApi) {

		final Interceptor interceptor = new Interceptor(interceptors);
		interceptor.addDefaultInterceptor((new DefaultSyncInterceptor(msgApi)));

		return interceptor;
	}
}
