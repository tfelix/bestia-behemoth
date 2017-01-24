package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.Props;
import net.bestia.messages.internal.entity.EntitySpawnMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * TODO Vorläufige Klasse.
 * 
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public class EntitySpawnActor extends BestiaRoutingActor {

	public static final String NAME = "entitySpawn";

	public EntitySpawnActor() {
		super(Arrays.asList(EntitySpawnMessage.class));
	}

	@Override
	protected void handleMessage(Object msg) {
		
		EntitySpawnMessage spmsg = (EntitySpawnMessage) msg;
		
		// TODO We only have bestia entities.
		final long eid = spmsg.getEntityId();

		final Props aiProps = getSpringProps(EntityAiActor.class, Long.valueOf(eid));
		getContext().actorOf(aiProps);
	}

}
