package net.bestia.zoneserver.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Displays basic stats about the health of the bestia zone server.
 * 
 * @author Thomas Felix
 *
 */
@RestController
public class HealthController {

	@RequestMapping("/health")
	public HealthStatus greeting() {
		return new HealthStatus();
	}
}
