package net.bestia.zoneserver.configuration;

import java.util.Date;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.MapParameter;
import net.bestia.zoneserver.environment.date.BestiaDate;
import net.bestia.zoneserver.map.path.AStarPathfinder;
import net.bestia.zoneserver.map.path.Pathfinder;

/**
 * Central bean definitions for the main bestia zoneserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Configuration
@Profile("production")
public class ZoneConfiguration {

	/**
	 * Gets the pathfinder implementation used by bestia.
	 * 
	 * @return A pathfinder.
	 */
	@Bean
	Pathfinder pathfinder() {
		return new AStarPathfinder();
	}

	/**
	 * Gets the current time of the bestia zoneserver.
	 * 
	 * @return The current time object.
	 */
	@Bean
	public BestiaDate bestiaDate(MapParameterDAO mapParamDao) {
		final MapParameter param = mapParamDao.findLatest();
		
		if(param == null) {
			return new BestiaDate();
		}
		
		final Date creationDate = param.getCreateDate();
		return BestiaDate.fromDate(creationDate);
	}

}
