package net.bestia.zoneserver.actor.connection;

import akka.actor.AbstractActor;

/**
 * After a connection is fully initialized we need to perform certain tasks to
 * reconnect the user back to its already spawned entities and also spawn its
 * master bestia in order to control it.
 * 
 * This actor performs this tasks.
 * 
 * @author Thomas Felix
 *
 */
public class InitializeConnectionActor extends AbstractActor {

	@Override
	public Receive createReceive() {
		// TODO Auto-generated method stub
		return null;
	}

}
