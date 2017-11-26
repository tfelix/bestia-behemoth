package net.bestia.memoryserver.configuration;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bestia.entity.component.Component;
import net.bestia.entity.component.interceptor.BaseComponentInterceptor;
import net.bestia.entity.component.interceptor.DefaultSyncInterceptor;
import net.bestia.entity.component.interceptor.Interceptor;
import net.bestia.messages.MessageApi;

@Configuration
public class ComponentInterceptorConfiguration {
	
	/**
	 * Adds a new default interceptor to the interceptor class.
	 * 
	 * @param interceptors
	 * @param msgApi
	 * @return
	 */
	@Bean
	public Interceptor defaultInterceptor(List<BaseComponentInterceptor<? extends Component>> interceptors,
			MessageApi msgApi) {

		final Interceptor interceptor = new Interceptor(interceptors);
		interceptor.addInterceptor(new DefaultSyncInterceptor(msgApi));

		return interceptor;
	}

}
