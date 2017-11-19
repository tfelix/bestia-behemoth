package net.bestia.zoneserver.actor.zone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.battle.AttackUseActor;
import net.bestia.zoneserver.actor.bestia.ActivateBestiaActor;
import net.bestia.zoneserver.actor.chat.ChatActor;
import net.bestia.zoneserver.actor.connection.LatencyManagerActor;
import net.bestia.zoneserver.actor.entity.EntityInteractionRequestActor;
import net.bestia.zoneserver.actor.entity.EntitySyncActor;
import net.bestia.zoneserver.actor.inventory.ListInventoryActor;
import net.bestia.zoneserver.actor.map.MapRequestChunkActor;
import net.bestia.zoneserver.actor.map.TilesetRequestActor;
import net.bestia.zoneserver.actor.ui.ClientVarActor;

/**
 * The ingestion extended actor is a development actor to help the transition
 * towards a cleaner actor massaging management. It serves as a proxy
 * re-directing the incoming messages towards the new system or to the legacy
 * system.
 * 
 * It is also possible to send this actor a list of classes. Instances of this
 * type of message are then send to the issuer of this request in the future.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ClientMessageActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "clientMessageIngest";

	/**
	 * This message is send towards actors (usually an IngestActor) which will
	 * then redirect all messages towards the actor.
	 *
	 */
	public static final class RedirectMessage {

		private final List<Class<? extends Object>> classes = new ArrayList<>();

		private RedirectMessage() {
			// no op
		}

		/**
		 * Creates a redirection message for the given classes. This message can
		 * be send to a {@link ClientMessageActor} to redirect the message flow.
		 * 
		 * @param classes
		 * @return A new redirection request message.
		 */
		@SafeVarargs
		public static RedirectMessage get(Class<? extends Object>... classes) {
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
		public List<Class<? extends Object>> getClasses() {
			return classes;
		}
	}

	private Map<Class<?>, List<ActorRef>> redirections = new HashMap<>();

	private ActorRef messageRouter;
	
	@Autowired
	public ClientMessageActor(ActorRef msgHub) {

		messageRouter = Objects.requireNonNull(msgHub);
	}

	@Override
	public void preStart() throws Exception {

		// === Login and connection ===
		SpringExtension.actorOf(getContext(), LatencyManagerActor.class);

		// === Bestias ===
		SpringExtension.actorOf(getContext(), ActivateBestiaActor.class);

		// === Inventory ===
		SpringExtension.actorOf(getContext(), ListInventoryActor.class, messageRouter);

		// === Map ===
		SpringExtension.actorOf(getContext(), MapRequestChunkActor.class, messageRouter);
		SpringExtension.actorOf(getContext(), TilesetRequestActor.class, messageRouter);

		// === Entities ===
		SpringExtension.actorOf(getContext(), EntityInteractionRequestActor.class, messageRouter);
		SpringExtension.actorOf(getContext(), EntitySyncActor.class, messageRouter);

		// === Attacking ===
		SpringExtension.actorOf(getContext(), AttackUseActor.class, messageRouter);

		// === UI ===
		SpringExtension.actorOf(getContext(), ClientVarActor.class, messageRouter);

		// === Chat ===
		SpringExtension.actorOf(getContext(), ChatActor.class, messageRouter);
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
	 * 
	 * @param requestedClasses
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
