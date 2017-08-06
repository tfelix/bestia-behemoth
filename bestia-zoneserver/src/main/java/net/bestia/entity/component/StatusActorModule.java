package net.bestia.entity.component;

import org.springframework.stereotype.Component;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.entity.component.StatusComponentActor;

/**
 * Constructs the component actor for status regeneration ticks.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class StatusActorModule extends ActorComponentFactoryModule<StatusComponent> {

	public StatusActorModule() {
		super(StatusComponent.class);
		// no op.
	}

	@Override
	protected ActorRef doBuildActor(ActorContext ctx, StatusComponent component) {
		return SpringExtension.actorOf(ctx, 
				StatusComponentActor.class,
				StatusComponentActor.NAME,
				component.getEntityId());
	}

}
