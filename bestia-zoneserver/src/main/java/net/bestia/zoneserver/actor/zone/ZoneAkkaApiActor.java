package net.bestia.zoneserver.actor.zone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.TypedActor;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.entity.EntityWorker;

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
	public ActorRef startActor(Class<? extends AbstractActor> actorClazz) {

		ActorRef actor = SpringExtension.actorOf(context.system(), actorClazz);

		LOG.info("Starting actor: {}, path: {}", actorClazz, actor.path().toString());

		return actor;
	}
	
	@Override
	public ActorRef startUnnamedActor(Class<? extends AbstractActor> actorClazz) {

		ActorRef actor = SpringExtension.unnamedActorOf(context.system(), actorClazz);

		LOG.info("Starting actor: {}, path: {}", actorClazz, actor.path().toString());

		return actor;
	}

	@Override
	public void sendToActor(ActorPath actorPath, Object message) {
		final ActorSelection selection = context.system().actorSelection(actorPath);
		selection.tell(message, ActorRef.noSender());
	}

	@Override
	public void sendEntityActor(long entityId, Object msg) {
		
		// Find the name.
		final String rawActorName = EntityActor.getActorName(entityId);
		final String actorName = AkkaCluster.getNodeName(EntityWorker.NAME, rawActorName);
		final ActorSelection selection = context.system().actorSelection(actorName);
		selection.tell(msg, ActorRef.noSender());
	}
}
