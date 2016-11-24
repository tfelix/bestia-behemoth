package net.bestia.zoneserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bestia.zoneserver.map.path.AStarPathfinder;
import net.bestia.zoneserver.map.path.Pathfinder;

/**
 * Central bean definitions for the main bestia zoneserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Configuration
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

}
