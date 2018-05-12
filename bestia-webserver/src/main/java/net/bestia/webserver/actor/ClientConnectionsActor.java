package net.bestia.webserver.actor;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.bestia.webserver.messages.web.ClientPayloadMessage;
import net.bestia.webserver.messages.web.CloseConnection;
import net.bestia.webserver.messages.web.OpenConnection;

import java.util.Objects;


/**
 * Holds a reference to all currently connected client sockets and manages them.
 *
 * @author Thomas Felix
 */
public class ClientConnectionsActor extends AbstractActor {

  private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

  private final ActorRef uplink;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule());

  private final BiMap<String, ActorRef> connections = HashBiMap.create();

  private ClientConnectionsActor(ActorRef uplink) {

    this.uplink = Objects.requireNonNull(uplink);
  }

  public static Props props(ActorRef uplink) {
    return Props.create(new Creator<ClientConnectionsActor>() {
      private static final long serialVersionUID = 1L;

      public ClientConnectionsActor create() throws Exception {
        return new ClientConnectionsActor(uplink);
      }
    }).withDeploy(Deploy.local());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(OpenConnection.class, this::handleClientSocketOpened)
            .match(ClientPayloadMessage.class, this::handleClientPayloadMessage)
            .match(CloseConnection.class, this::handleClientSocketClosed)
            .match(Terminated.class, this::handleClosedConnection)
            .build();
  }

  /**
   * Received if a message from a client is received.
   *
   * @param msg
   */
  private void handleClientPayloadMessage(ClientPayloadMessage msg) {

    final ActorRef socketActor = connections.get(msg.getSessionId());

    if (socketActor == null) {
      LOG.debug("No active connection for session: {}", msg.getSessionId());
      return;
    }

    socketActor.tell(msg, getSelf());
  }

  private void handleClientSocketOpened(OpenConnection msg) {

    final Props socketProps = ClientConnectionActor.props(uplink, mapper, msg.getSession());
    final String actorName = String.format("socket-%s", msg.getSessionId());
    final ActorRef socketActor = getContext().actorOf(socketProps, actorName);

    getContext().watch(socketActor);
    connections.put(msg.getSessionId(), socketActor);

    LOG.debug("Client {} opened connection. Starting actor {}.", msg.getSessionId(), socketActor);

  }

  private void handleClientSocketClosed(CloseConnection msg) {
    LOG.debug("Client {} closed connection. Stopping actor.", msg.getSessionId());

    final ActorRef connectionActor = connections.get(msg.getSessionId());
    if (connectionActor != null) {
      connectionActor.tell(PoisonPill.getInstance(), getSelf());
    }
  }

  private void handleClosedConnection(Terminated msg) {
    LOG.debug("Removing closed connection actor: {}", msg.actor());

    connections.inverse().remove(msg.actor());
  }
}