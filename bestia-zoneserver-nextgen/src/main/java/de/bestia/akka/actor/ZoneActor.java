package de.bestia.akka.actor;

import java.util.Objects;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import de.bestia.akka.message.InputMessage;

public class ZoneActor extends UntypedActor {
	
	private final String zoneName;
	
	public ZoneActor(String zoneName) {
		this.zoneName = Objects.requireNonNull(zoneName);
	}

	public static Props props(final String zoneName) {
		return Props.create(new Creator<ZoneActor>() {
			private static final long serialVersionUID = 1L;

			public ZoneActor create() throws Exception {
				return new ZoneActor(zoneName);
			}
		});
	}
	
	public String getZoneName() {
		return zoneName;
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if(!(message instanceof InputMessage)) {
			unhandled(message);
		}
		
		final InputMessage msg = (InputMessage) message;
		
		System.out.println(String.format("Zone %s received: %s", getZoneName(), msg.getPayload()));
	}
}
