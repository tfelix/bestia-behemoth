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
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.SpringExtension;

public class ZoneAkkaApiActor implements ZoneAkkaApi {
	
	private final static Logger LOG = LoggerFactory.getLogger(ZoneAkkaApiActor.class);

	private final ActorContext context;

	public ZoneAkkaApiActor() {

		this.context = TypedActor.context();
	}

	@Override
	public void sendToClient(JsonMessage message) {
		AkkaSender.sendClient(context, message);
	}

	@Override
	public void sendActiveInRangeClients(EntityJsonMessage message) {
		AkkaSender.sendActiveInRangeClients(context, message);
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
		AkkaSender.sendEntityActor(context, entityId, msg);
	}
}
