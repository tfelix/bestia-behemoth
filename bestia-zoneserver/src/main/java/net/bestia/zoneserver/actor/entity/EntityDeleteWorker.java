package net.bestia.zoneserver.actor.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * This worker actor listens for incoming entity IDs and upon receiving they
 * will get terminated and their resources freed.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityDeleteWorker extends AbstractActor {

	public static final String NAME = "entityDelete";

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Long.class, this::handleDeleteEntity)
				.build();
	}

	/**
	 * Handles the deletion of the entity id.
	 * 
	 * @param entityId
	 *            The ID to free all resources and to delete.
	 */
	private void handleDeleteEntity(Long entityId) {

		SpringExtension.unnamedActorOf(context(), EntityDeleteActor.class, entityId);

	}

}
