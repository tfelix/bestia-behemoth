package net.bestia.zoneserver.actor.rest;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;

/**
 * Simple Test actor which will automatically generate the needed filter and
 * also connects to the main actor.
 * 
 * @author Thomas Felix
 *
 */
public abstract class BAbstractActor extends AbstractActor {
	
	private static class BReceiveBuilder extends ReceiveBuilder {
		
		
		
	}

	@Override
	public final Receive createReceive() {
		
		final BReceiveBuilder builder = createBestiaReceive(new BReceiveBuilder());
		
		// Send the cached messages.
		
		return builder.build();
	}

	protected abstract BReceiveBuilder createBestiaReceive(BReceiveBuilder bReceiveBuilder);
}
