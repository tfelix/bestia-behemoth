package de.bestia.next.zoneserver.actor;

import java.util.HashMap;
import java.util.Map;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.japi.Creator;
import de.bestia.next.zoneserver.message.CacheAnswerMessage;
import de.bestia.next.zoneserver.message.CacheRequestMessage;
import de.bestia.next.zoneserver.message.InputMessage;

public class ZoneRouter extends UntypedActor {

	private final Map<String, ActorRef> actorMap = new HashMap<>();
	private final ActorRef cacheActor;

	public ZoneRouter() {
		final UntypedActorContext ctx = getContext();

		final ActorRef ref1 = ctx.actorOf(ZoneActor.props("zone1"));
		final ActorRef ref2 = ctx.actorOf(ZoneActor.props("zone2"));

		actorMap.put("zone1", ref1);
		actorMap.put("zone2", ref2);
		
		this.cacheActor = ctx.actorOf(CacheActor.props());
	}

	public static Props props() {
		return Props.create(new Creator<ZoneRouter>() {
			private static final long serialVersionUID = 1L;

			public ZoneRouter create() throws Exception {
				return new ZoneRouter();
			}
		});
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof InputMessage) {

			// Ask where to send this message.
			final InputMessage msg = (InputMessage) message;
			final CacheRequestMessage crm = new CacheRequestMessage(Long.toString(msg.getPlayerBestiaId()), msg);
			
			cacheActor.tell(crm, getSelf());

		} else if (message instanceof CacheAnswerMessage) {

			// Deliver it.
			final CacheAnswerMessage msg = (CacheAnswerMessage) message;

			if (msg.getValue() == null) {
				// No receiver found.
				unhandled(message);
				return;
			}
			final String key = (String) msg.getValue();
			final ActorRef ref = actorMap.get(key);

			ref.tell(msg.getOrigMessage(), getSelf());

		} else {
			unhandled(message);
		}
	}

}
