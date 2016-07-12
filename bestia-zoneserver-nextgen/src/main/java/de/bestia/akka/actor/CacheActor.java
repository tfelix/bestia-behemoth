package de.bestia.akka.actor;

import java.util.Map;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import de.bestia.akka.message.CacheAnswerMessage;
import de.bestia.akka.message.CacheRequestMessage;

public class CacheActor extends UntypedActor {

	final HazelcastInstance instance;

	public CacheActor() {
		Config cfg = new Config();

		this.instance = Hazelcast.newHazelcastInstance(cfg);

		Map<Integer, String> mapCustomers = instance.getMap("id_to_zone");

		mapCustomers.put(1, "zone1");
		mapCustomers.put(2, "zone2");
		mapCustomers.put(3, "zone1");
	}
	
	public static Props props() {
		return Props.create(new Creator<CacheActor>() {
			private static final long serialVersionUID = 1L;

			public CacheActor create() throws Exception {
				return new CacheActor();
			}
		});
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (!(message instanceof CacheRequestMessage)) {
			unhandled(message);
		}

		final CacheRequestMessage msg = (CacheRequestMessage) message;

		final Map<Integer, String> mapId2Zone = instance.getMap("id_to_zone");
		
		final Integer key = Integer.parseInt(msg.getKey());

		if (mapId2Zone.containsKey(key)) {
			final Object value = mapId2Zone.get(key);
			getContext().parent().tell(new CacheAnswerMessage(msg, value, msg.getOriginalMessage()), getSelf());
		} else {
			getContext().parent().tell(new CacheAnswerMessage(msg, null, null), getSelf());
		}

	}
	
	@Override
	public void postStop() throws Exception {
		super.postStop();
		
		instance.getLifecycleService().shutdown();
	}

}
