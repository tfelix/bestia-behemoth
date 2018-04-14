package net.bestia.webserver.actor;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.client.ClientConnectMessage;
import net.bestia.messages.client.FromClientEnvelop;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LogoutMessage;
import net.bestia.messages.login.LogoutState;
import net.bestia.webserver.messages.web.ClientPayloadMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ClientConnectionActor extends AbstractActor {

  private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

  private Cancellable deathTimer = getContext().system()
          .scheduler()
          .scheduleOnce(
                  Duration.create(10, TimeUnit.SECONDS),
                  getSelf(), PoisonPill.getInstance(), getContext().dispatcher(), null);

  private final ActorRef uplink;
  private final WebSocketSession session;
  private final ObjectMapper mapper;

  private final AbstractActor.Receive unauthenticated;
  private final AbstractActor.Receive authenticated;

  /**
   * Account id is set as soon as the connection gets confirmed from the
   * server.
   */
  private long accountId;

  public ClientConnectionActor(ActorRef uplink, ObjectMapper mapper, WebSocketSession session) {

    this.uplink = Objects.requireNonNull(uplink);
    this.mapper = Objects.requireNonNull(mapper);
    this.session = Objects.requireNonNull(session);

    // Setup the two behaviours.
    unauthenticated = receiveBuilder()
            .match(ClientPayloadMessage.class, this::handleClientPayloadUnauthenticated)
            .build();

    authenticated = receiveBuilder()
            .match(LogoutMessage.class, this::handleServerLogout)
            .match(AccountMessage.class, this::sendToClient)
            .match(ClientPayloadMessage.class, this::handleClientPayload)
            .build();

  }

  static Props props(ActorRef uplink, ObjectMapper mapper, WebSocketSession session) {
    return Props.create(new Creator<ClientConnectionActor>() {
      private static final long serialVersionUID = 1L;

      public ClientConnectionActor create() {
        return new ClientConnectionActor(uplink, mapper, session);
      }
    }).withDeploy(Deploy.local());
  }

  @Override
  public Receive createReceive() {
    return unauthenticated;
  }

  @Override
  public void postStop() {

    // Send the server that we have closed the connection.
    // If the websocket session is still opened and we are terminated from
    // the akka side, close it here.
    if (session.isOpen()) {
      LOG.debug("Closing connection to {}.", session.getRemoteAddress().toString());
      try {
        session.close(CloseStatus.NORMAL);
      } catch (IOException e1) {
        // no op.
      }
    }

    final ClientConnectMessage ccsmsg = new ClientConnectMessage(accountId, getSelf());
    uplink.tell(ccsmsg, getSelf());
  }

  /**
   * The server requests a logout.
   */
  private void handleServerLogout(LogoutMessage msg) {

    // Send the message to the client like every other message.
    sendToClient(msg);

    // Kill ourself.
    getContext().stop(getSelf());
  }

  /**
   * Payload is send from the client to the server.
   *
   * @param payload Payload data from the client.
   */
  private void handleClientPayload(ClientPayloadMessage payload) throws IOException {
    // We only accept auth messages if we are not connected. Every other
    // message will disconnect the client.

    try {
      // Turn the text message into a bestia message.
      JsonMessage msg = mapper.readValue(payload.getMessage(), JsonMessage.class);

      // Regenerate the account id from this session. (we dont trust
      // the client to tell us the right account id).
      msg = msg.createNewInstance(accountId);

      LOG.debug("Client sending: {}.", msg.toString());
      uplink.tell(wrap(accountId, msg), getSelf());

    } catch (IOException e) {
      LOG.warning("Malformed message. Client: {}, Payload: {}, Error: {}.",
              session.getRemoteAddress(),
              payload,
              e.toString());
      throw e;
    }
  }

  /**
   * Payload is send from the client to the server.
   *
   * @param payload Payload data from the client.
   */
  private void handleClientPayloadUnauthenticated(ClientPayloadMessage payload) throws IOException {
    // We only accept auth messages if we are not connected. Every other
    // message will disconnect the client.
    final LoginAuthMessage loginReqMsg = mapper.readValue(payload.getMessage(), LoginAuthMessage.class);
    performTemporaryLoginFlow(loginReqMsg);
  }

  private void performTemporaryLoginFlow(LoginAuthMessage loginMsg) {
    LOG.warning("Temp. login flow must be replaced with a proper one when known how to do inter service communication");
    String validLoginToken = "944baa39-d1ba-48be-a541-664fbb2e6fae";
    if(!loginMsg.getJwtToken().equals(validLoginToken)) {

      final LogoutMessage reply = new LogoutMessage(LogoutState.NO_REASON, "");
      sendToClient(reply);
      getContext().stop(getSelf());
    } else {
      LOG.debug("Client login accepted.");
      // This acc id is verified by the server.
      // TODO Must be returned from JWT token.
      this.accountId = 1;

      getContext().become(authenticated);

      // Announce to the server that we have a fully connected client.
      final ClientConnectMessage cccm = new ClientConnectMessage(
              accountId,
              getSelf()
      );
      uplink.tell(wrap(accountId, cccm), getSelf());
    }

    deathTimer.cancel();
    deathTimer = null;
  }

  private void sendToClient(Object message) {
    // Send the payload to the client.
    try {
      final String payload = mapper.writeValueAsString(message);
      LOG.debug("Server sending: {}.", payload);
      session.sendMessage(new TextMessage(payload));
    } catch (IOException | IllegalStateException e) {
      // Could not send to client.
      LOG.error("Could not send message: {}.", message.toString(), e);
      getContext().stop(getSelf());
    }
  }

  private FromClientEnvelop wrap(Long accountId, Object message) {
    return new FromClientEnvelop(accountId, message);
  }
}
