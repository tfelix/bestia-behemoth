package net.bestia.zoneserver.actor.zone;

import akka.actor.AbstractActor;

/**
 * The ingestion extended actor is a development actor to help the transition
 * towards a cleaner actor massaging managament. It serves as a proxi
 * re-directing the incoming messages towards the new system or to the legacy
 * system.
 * 
 * @author Thomas Felix
 *
 */
public class IngestExActor extends AbstractActor {

	@Override
	public Receive createReceive() {
		// TODO Auto-generated method stub
		return null;
	}

}
