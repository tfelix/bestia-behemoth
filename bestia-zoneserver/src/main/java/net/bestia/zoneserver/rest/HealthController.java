package net.bestia.zoneserver.rest;


/**
 * Displays basic stats about the health of the bestia zone server. Temporär
 * entfernt weil buggy as fuck.
 * 
 * @author Thomas Felix
 *
 */
// @RestController
public class HealthController {

	// @RequestMapping("/health")
	public HealthStatus greeting() {
		return new HealthStatus();
	}
}
