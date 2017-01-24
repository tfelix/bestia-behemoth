package net.bestia.zoneserver.actor.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.Props;
import net.bestia.messages.internal.EntitySpawnMessage;
import net.bestia.zoneserver.actor.BestiaActor;

/**
 * TOD Vorläufige Klasse.
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public class EntityManagerActor extends BestiaActor {
	
	public static final String NAME = "entities";

	@Override
	public void onReceive(Object msg) throws Throwable {
		
		if(msg instanceof EntitySpawnMessage) {
			spawnEntity((EntitySpawnMessage) msg);
		}
		
	}

	private void spawnEntity(EntitySpawnMessage msg) {
		
		// TODO We only have bestia entities.
		final long eid = msg.getEntityId();
		
		final Props aiProps = getSpringProps(EntityAiActor.class, Long.valueOf(eid));
		getContext().actorOf(aiProps);
	}

	
}
