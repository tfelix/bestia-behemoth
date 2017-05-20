package net.bestia.zoneserver.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorContext;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.TypedActor;
import akka.actor.UntypedActor;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;

public class ZoneAkkaApiActor implements ZoneAkkaApi {
	
	private final static Logger LOG = LoggerFactory.getLogger(ZoneAkkaApiActor.class);

	private final ActorSelection sendClientActor;
	private final ActorSelection sendActiveClientActor;

	private final ActorContext context;

	public ZoneAkkaApiActor() {

		this.context = TypedActor.context();

		this.sendClientActor = context.actorSelection(AkkaCluster.getNodeName(SendClientActor.NAME));
		this.sendActiveClientActor = context.actorSelection(AkkaCluster.getNodeName(ActiveClientUpdateActor.NAME));
	}

	@Override
	public void sendToClient(JsonMessage message) {
		sendClientActor.tell(message, ActorRef.noSender());
	}

	@Override
	public void sendActiveInRangeClients(EntityJsonMessage message) {
		sendActiveClientActor.tell(message, ActorRef.noSender());
	}

	@Override
	public void sendToActor(String actorName, Object message) {
		context.actorSelection(AkkaCluster.getNodeName(actorName)).tell(message, ActorRef.noSender());
	}
	
	@Override
	public ActorRef startActor(Class<? extends UntypedActor> actorClazz) {

		ActorRef actor = SpringExtension.actorOf(context.system(), actorClazz);

		LOG.info("Starting actor: {}, path: {}", actorClazz, actor.path().toString());

		return actor;
	}
	
	@Override
	public ActorRef startUnnamedActor(Class<? extends UntypedActor> actorClazz) {

		ActorRef actor = SpringExtension.unnamedActorOf(context.system(), actorClazz);

		LOG.info("Starting actor: {}, path: {}", actorClazz, actor.path().toString());

		return actor;
	}

	@Override
	public void sendToActor(ActorPath actorPath, Object message) {
		final ActorSelection selection = context.system().actorSelection(actorPath);
		selection.tell(message, ActorRef.noSender());
	}
}
