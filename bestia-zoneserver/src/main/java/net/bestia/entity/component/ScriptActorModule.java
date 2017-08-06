package net.bestia.entity.component;

import org.springframework.stereotype.Component;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.entity.component.ScriptComponentActor;

/**
 * Builds a script component for the entity actor.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ScriptActorModule extends ActorComponentFactoryModule<ScriptComponent> {

	public ScriptActorModule() {
		super(ScriptComponent.class);
		// no op.
	}

	@Override
	protected ActorRef doBuildActor(ActorContext ctx, ScriptComponent component) {
		return SpringExtension.actorOf(ctx, 
				ScriptComponentActor.class, 
				ScriptComponentActor.NAME,
				component.getEntityId());
	}

}
