package net.bestia.zoneserver.actor.entity;

import akka.persistence.UntypedPersistentActor;

/**
 * The {@link EntityActor} is a persistent actor managing all aspects of a
 * spawned entity. This means it will keep references to AI actors or attached
 * script actors.
 * 
 * @author Thomas Felix
 *
 */
public class EntityActor extends UntypedPersistentActor {
	
	private final static String PERSISTED_NAME = "entity-%d";
	private long entityId;

	@Override
	public String persistenceId() {
		return String.format(PERSISTED_NAME, entityId);
	}

	@Override
	public void onReceiveCommand(Object msg) throws Throwable {
		// TODO Auto-generated method stub

	}

	@Override
	public void onReceiveRecover(Object msg) throws Throwable {
		// TODO Auto-generated method stub

	}

}
