package net.bestia.zoneserver.configuration;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.interceptor.ComponentInterceptor;
import net.bestia.entity.recycler.ComponentCleaner;
import net.bestia.entity.recycler.EntityCache;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.MapParameter;
import net.bestia.zoneserver.environment.date.BestiaDate;

/**
 * Central bean definitions for the main bestia zoneserver. Some beans require a
 * special setup. These beans are setup via this configuration here.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Configuration
public class ZoneBaseConfiguration {

	private final static Logger LOG = LoggerFactory.getLogger(ZoneBaseConfiguration.class);

	/**
	 * Gets the current time of the bestia zoneserver.
	 * 
	 * @return The current time object.
	 */
	// @Bean
	public BestiaDate bestiaDate(MapParameterDAO mapParamDao) {
		final MapParameter param = mapParamDao.findFirstByOrderByIdDesc();

		if (param == null) {
			return new BestiaDate();
		}

		final Date creationDate = param.getCreateDate();
		return BestiaDate.fromDate(creationDate);
	}

	@Bean
	public EntityService entityService(HazelcastInstance hz, ApplicationContext ctx) {

		final EntityService entityService = new EntityService(hz);

		@SuppressWarnings("rawtypes")
		final Map<String, ComponentInterceptor> interceptors = ctx.getBeansOfType(ComponentInterceptor.class);

		LOG.info("Found {} component interceptors. Adding to entity service.", interceptors.size());

		for (ComponentInterceptor<?> interceptor : interceptors.values()) {

			entityService.addInterceptor(interceptor);
			LOG.debug("Added component interceptor: {}", interceptor.getClass().getSimpleName());

		}

		return entityService;
	}

	@Bean
	public EntityCache entityRecycler(
			StaticConfigService config,
			List<ComponentCleaner<? extends Component>> recyclers) {
		return new EntityCache(config.getEntityBufferSize(), recyclers);
	}

}