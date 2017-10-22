package net.bestia.webserver.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.Creator;
import net.bestia.webserver.messages.web.ClientPayloadMessage;
import net.bestia.webserver.messages.web.CloseConnection;
import net.bestia.webserver.messages.web.PrepareConnection;
import net.bestia.webserver.messages.web.ZoneConnectionAccepted;

public class ConnectionHandshakeActor extends AbstractActor {

	private ActorRef uplink;
	private final ObjectMapper mapper = new ObjectMapper();

	private final Map<String, ActorRef> uidToActorAuth = new HashMap<>();
	private final Map<ActorRef, String> actorToUidAuth = new HashMap<>();

	private final Map<String, ActorRef> uidToActor = new HashMap<>();
	private final Map<ActorRef, String> actorToUid = new HashMap<>();

	private ConnectionHandshakeActor(ActorRef uplink) {

		this.uplink = Objects.requireNonNull(uplink);
	}

	public static Props props(ActorRef uplink) {
		return Props.create(new Creator<ConnectionHandshakeActor>() {
			private static final long serialVersionUID = 1L;

			public ConnectionHandshakeActor create() throws Exception {
				return new ConnectionHandshakeActor(uplink);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(PrepareConnection.class, this::handlePrepareConnection)
				.match(ZoneConnectionAccepted.class, this::handleAcceptedConnection)
				.match(CloseConnection.class, this::handleClientSocketClosed)
				.match(ClientPayloadMessage.class, this::handleClientPayloadMessage)
				.match(Terminated.class, this::handleClosedConnection)
				.build();
	}

	private void handleClientPayloadMessage(ClientPayloadMessage msg) {

		ActorRef actor = uidToActor.get(msg.getUid());

		if (actor != null) {
			actor.tell(msg.getMessage(), getSelf());
			return;
		}

		// Try to send to auth actor.
		actor = uidToActorAuth.get(msg.getUid());

		if (actor != null) {
			actor.tell(msg.getMessage(), getSelf());
		}
	}

	private void handleClientSocketClosed(CloseConnection msg) {

		// get the mapped uid.
		String actorUid = uidMapper.get(msg.getUid());
		
		if(actorUid == null) {
			return;
		}
		
		ActorRef actor = uidToActor.get(actorUid);

		if (actor != null) {
			actor.tell(PoisonPill.getInstance(), getSelf());
		}

		actor = uidToActorAuth.get(actorUid);

		if (actor != null) {
			actor.tell(PoisonPill.getInstance(), getSelf());
		}
	}

	private void handleClosedConnection(Terminated msg) {
		
		

		// Check if this is an auth actor.
		String uid = actorToUidAuth.get(msg.actor());
		actorToUidAuth.remove(msg.actor());
		if (uid != null) {
			uidToActorAuth.remove(uid);
		}

		uid = actorToUid.get(msg.actor());
		actorToUid.remove(msg.actor());
		if (uid != null) {
			uidToActor.remove(uid);
		}
	}

	private void handlePrepareConnection(PrepareConnection msg) {
		

		final String actorName = String.format("socket-auth-%s", actorUid);

		final Props socketProps = ClientAuthActor.props(msg.getSessionId(), msg.getSession(), mapper, uplink);
		final ActorRef socketActor = getContext().actorOf(socketProps, actorName);

		uidToActorAuth.put(actorUid, socketActor);
		actorToUidAuth.put(socketActor, actorUid);
	}

	/**
	 * Server accepted connection.
	 * 
	 * @param msg
	 */
	private void handleAcceptedConnection(ZoneConnectionAccepted msg) {
		
		String actorUid = UUID.randomUUID().toString();
		uidMapper.put(msg.getUid(), actorUid);

		final String actorName = String.format("socket-%s", msg.getUid());

		final Props socketProps = ClientSocketActor.props(msg,
				mapper,
				uplink).withDeploy(Deploy.local());
		final ActorRef socketActor = getContext().actorOf(socketProps, actorName);
		actorToUid.put(socketActor, msg.getUid());
		uidToActor.put(msg.getUid(), socketActor);

		// Tell the client login was succesful.
		socketActor.tell(msg.getLoginMessage(), getSelf());
	}
}
