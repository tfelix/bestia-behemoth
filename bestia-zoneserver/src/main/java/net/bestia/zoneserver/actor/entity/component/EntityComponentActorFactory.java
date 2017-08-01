package net.bestia.zoneserver.actor.entity.component;

import java.util.Objects;

import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.entity.EntityService;

/**
 * Depending on the given component ID this factory will create an actor
 * suitable for the component.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class EntityComponentActorFactory {

	private final EntityService entityService;

	public EntityComponentActorFactory(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	public ActorRef startActor(long componentId) {
		// TODO Auto-generated method stub
		return null;
	}

}
