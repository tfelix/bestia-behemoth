package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Small test configuration which scans for component inside the actor classes.
 *
 */
@Configuration
@ComponentScan("net.bestia.zoneserver.actor")
public class ActorTestConfig {

}
