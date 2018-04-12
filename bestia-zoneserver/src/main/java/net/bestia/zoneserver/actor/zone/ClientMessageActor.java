package net.bestia.zoneserver.actor.zone;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.client.ClientFromMessageEnvelope;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.bestia.BestiaInfoActor;
import net.bestia.zoneserver.actor.chat.ChatActor;
import net.bestia.zoneserver.actor.connection.ClientConnectionManagerActor;
import net.bestia.zoneserver.actor.connection.LatencyManagerActor;
import net.bestia.zoneserver.actor.entity.EntitySyncRequestActor;
import net.bestia.zoneserver.actor.map.MapRequestChunkActor;
import net.bestia.zoneserver.actor.map.TilesetRequestActor;
import net.bestia.zoneserver.actor.routing.RegisterEnvelopeMessage;
import net.bestia.zoneserver.actor.ui.ClientVarActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.Map.Entry;

/**
 * The ingestion extended actor is a development actor to help the transition
 * towards a cleaner actor massaging management. It serves as a proxy
 * re-directing the incoming messages towards the new system or to the legacy
 * system.
 * <p>
 * It is also possible to send this actor a list of classes. Instances of this
 * type of message are then send to the issuer of this request in the future.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
public class ClientMessageActor extends ClientMessageDigestActor {

  private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

  public static final String NAME = "clientMessages";

  /**
   * This message is send towards actors (usually an IngestActor) which will
   * then redirect all messages towards the actor.
   */
  public static final class RedirectMessage {

    private final ArrayList<Class<?>> classes = new ArrayList<>();

    private RedirectMessage() {
      // no op
    }

    /**
     * Creates a redirection message for the given classes. This message can
     * be send to a {@link ClientMessageActor} to redirect the message flow.
     *
     * @return A new redirection request message.
     */
    public static RedirectMessage get(Class<?>... classes) {
      RedirectMessage req = new RedirectMessage();
      req.classes.addAll(Arrays.asList(classes));
      return req;
    }

    /**
     * Returns the list of classes of messages which should be redirected
     * towards the requesting actor.
     *
     * @return A list of classes.
     */
    public List<Class<?>> getClasses() {
      return classes;
    }
  }

  private final Map<Class<?>, List<ActorRef>> redirections = new HashMap<>();
  private final ActorRef postmaster;

  @Autowired
  public ClientMessageActor(ActorRef postmaster) {

    this.postmaster = Objects.requireNonNull(postmaster);
  }

  @Override
  public void preStart() {

    // === Connection ===
    SpringExtension.actorOf(getContext(), ClientConnectionManagerActor.class);
    SpringExtension.actorOf(getContext(), LatencyManagerActor.class);

    // === Bestias ===
    // SpringExtension.actorOf(getContext(), ActivateBestiaActor.class);
    SpringExtension.actorOf(getContext(), BestiaInfoActor.class);

    // === Map ===
    SpringExtension.actorOf(getContext(), MapRequestChunkActor.class);
    SpringExtension.actorOf(getContext(), TilesetRequestActor.class);

    // === Entities ===
    // SpringExtension.actorOf(getContext(), EntityInteractionRequestActor.class);
    // SpringExtension.actorOf(getContext(), PlayerMoveRequestActor.class);
    SpringExtension.actorOf(getContext(), EntitySyncRequestActor.class);

    // === Attacking ===
    // SpringExtension.actorOf(getContext(), AttackUseActor.class);

    // === UI ===
    SpringExtension.actorOf(getContext(), ClientVarActor.class);

    // === Chat ===
    SpringExtension.actorOf(getContext(), ChatActor.class);

    final RegisterEnvelopeMessage registerEnvelopeMessage = new RegisterEnvelopeMessage(ClientFromMessageEnvelope.class, getSelf());
    postmaster.tell(registerEnvelopeMessage, getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(RedirectMessage.class, this::handleMessageRedirectRequest)
            .match(Terminated.class, this::handleRouteeStopped)
            .matchAny(this::handleIncomingMessage)
            .build();
  }

  /**
   * Adds the incoming class names towards the redirection methods.
   */
  private void handleMessageRedirectRequest(RedirectMessage requestedClasses) {

    LOG.debug("Installing route for: {} to: {}.", requestedClasses.getClasses(), getSender());

    requestedClasses.getClasses().forEach(clazz -> {

      if (!redirections.containsKey(clazz)) {
        redirections.put(clazz, new ArrayList<>());
      }

      // If a actor terminates we must delete him from our routing list.
      final ActorRef routee = getSender();
      getContext().watch(routee);

      redirections.get(clazz).add(routee);
    });
  }

  /**
   * Called if a routee has stopped working. Must be deleted from the list.
   */
  private void handleRouteeStopped(Terminated msg) {

    // Maybe the complete gets empty and can be removed.
    Class<?> classToDelete = null;

    for (Entry<Class<?>, List<ActorRef>> entry : redirections.entrySet()) {
      if (entry.getValue().contains(msg.actor())) {

        entry.getValue().remove(msg.getActor());

        LOG.debug("Deleting dead actor route: {}.", msg.actor());

        if (entry.getValue().isEmpty()) {
          classToDelete = entry.getKey();
        }
      }
    }

    if (classToDelete != null) {
      redirections.remove(classToDelete);
    }
  }

  /**
   * Checks if a sub-actor wants to redirect this message and if so deliver it
   * to all subscribed actors.
   */
  private void handleIncomingMessage(Object msg) {

    if (redirections.containsKey(msg.getClass())) {

      final List<ActorRef> routees = redirections.get(msg.getClass());
      routees.forEach(ref -> {
        LOG.debug("Client message forwarding: {} to {}.", msg, ref);
        ref.forward(msg, getContext());
      });

    } else {
      unhandled(msg);
    }
  }
}
