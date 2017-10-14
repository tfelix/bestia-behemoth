package net.bestia.zoneserver.configuration;

import java.util.Date;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.entity.EntityService;
import net.bestia.entity.ZoneEntityService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.EntityCache;
import net.bestia.entity.component.interceptor.BaseComponentInterceptor;
import net.bestia.entity.component.interceptor.Interceptor;
import net.bestia.messages.MessageApi;
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

	//private final static Logger LOG = LoggerFactory.getLogger(ZoneBaseConfiguration.class);

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
	@Primary
	public EntityCache entityRecycler(
			StaticConfigService config,
			List<BaseComponentInterceptor<? extends Component>> recyclers) {
		return new EntityCache(config.getEntityBufferSize(), recyclers);
	}
	
	@Bean
	public EntityService entityService(HazelcastInstance hz,
			MessageApi akkaApi,
			Interceptor interceptor,
			EntityCache cache) {
		return new ZoneEntityService(hz, akkaApi, interceptor, cache);
	}

}
