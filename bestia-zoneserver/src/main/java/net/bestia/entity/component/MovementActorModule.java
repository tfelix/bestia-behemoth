package net.bestia.entity.component;

import org.springframework.stereotype.Component;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.entity.component.MovementComponentActor;

/**
 * Constructs the necessary movement component actors.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class MovementActorModule extends ActorComponentFactoryModule<PositionComponent> {

	public MovementActorModule() {
		super(PositionComponent.class);
		// no op.
	}

	@Override
	protected ActorRef doBuildActor(ActorContext ctx, PositionComponent component) {
		return SpringExtension.actorOf(ctx, 
				MovementComponentActor.class,
				MovementComponentActor.NAME,
				component.getEntityId());
	}

}
