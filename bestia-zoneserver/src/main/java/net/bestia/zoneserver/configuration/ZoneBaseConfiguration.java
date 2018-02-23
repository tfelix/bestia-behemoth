package net.bestia.zoneserver.configuration;

import java.util.Date;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import net.bestia.entity.EntityCache;
import bestia.model.dao.MapParameterDAO;
import bestia.model.domain.MapParameter;
import net.bestia.zoneserver.environment.date.BestiaDate;

/**
 * Central bean definitions for the main bestia zoneserver. Some beans require a
 * special setup. These beans are setup via this configuration here.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class ZoneBaseConfiguration {

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
	public EntityCache entityRecycler(StaticConfigService config) {
		return new EntityCache(config.getEntityBufferSize());
	}
}
