package net.bestia.zoneserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
import net.bestia.zoneserver.actor.notify.EntityContextActor;
import net.bestia.zoneserver.entity.EntityContext;
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

	/**
	 * Returns the {@link EntityContext} which is used by the entities itself to
	 * communicate back into the bestia system.
	 * 
	 * @param system
	 *            The current {@link ActorSystem}.
	 * @return The {@link EntityContext}.
	 */
	@Bean
	EntityContext entityContext(ActorSystem system) {
		final SpringExt ext = SpringExtension.PROVIDER.get(system);
		final Props props = ext.props(EntityContextActor.class);
		final ActorRef actor = system.actorOf(props, EntityContextActor.NAME);

		return new EntityContext(actor);
	}

}
