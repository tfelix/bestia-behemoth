package net.bestia.zoneserver.actor.zone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.ComponentMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.misc.PongMessage;
import net.bestia.messages.web.AccountLoginRequest;
import net.bestia.messages.web.ServerStatusMessage;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.connection.ConnectionManagerActor;
import net.bestia.zoneserver.actor.entity.ComponentRedirectionActor;
import net.bestia.zoneserver.actor.rest.RequestLoginActor;
import net.bestia.zoneserver.actor.rest.RequestServerStatusActor;
import net.bestia.zoneserver.actor.ui.ClientVarActor;

/**
 * The ingestion extended actor is a development actor to help the transition
 * towards a cleaner actor massaging managment. It serves as a proxy
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
public class IngestExActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "ingestEx";

	/**
	 * This message is send towards actors (usually an IngestActor) which will
	 * then redirect all messages towards the actor.
	 *
	 */
	public static final class RedirectMessage {

		private List<Class<? extends Object>> classes = new ArrayList<>();

		private RedirectMessage() {
			// no op
		}

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
	
	private ActorRef componentRedirActor;

	public IngestExActor() {
		
		// Setup the internal sub-actors of the ingest actor first.
		componentRedirActor = SpringExtension.actorOf(getContext(), ComponentRedirectionActor.class);

		// === UI ===
		SpringExtension.actorOf(getContext(), ClientVarActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ComponentMessage.class, msg -> {
					componentRedirActor.tell(msg, getSelf());
				})
				.match(PongMessage.class, this::redirectConnection)
				.match(ClientConnectionStatusMessage.class, this::redirectConnection)
				.match(RedirectMessage.class, this::handleMessageRedirectRequest)

				// Temp
				.match(AccountLoginRequest.class, msg -> {
					AkkaSender.sendToActor(getContext(), RequestLoginActor.NAME, msg, getSender());
				})
				.match(ServerStatusMessage.Request.class, msg -> {
					AkkaSender.sendToActor(getContext(), RequestServerStatusActor.NAME, msg, getSender());
				})

				.matchAny(this::handleIncomingMessage)
				.build();
	}

	/**
	 * Adds the incoming class names towards the redirection methods.
	 * 
	 * @param requestedClasses
	 */
	private void handleMessageRedirectRequest(RedirectMessage requestedClasses) {

		LOG.debug("Installing message route for: {} to: {}.", requestedClasses.getClasses(), getSender());

		requestedClasses.getClasses().forEach(clazz -> {
			if (!redirections.containsKey(clazz)) {
				redirections.put(clazz, new ArrayList<>());
			}
			redirections.get(clazz).add(getSender());
		});
	}

	/**
	 * Redirect all other messages to the legacy actor.
	 * 
	 * @param msg
	 */
	private void redirectLegacy(Object msg) {
		LOG.debug("IngestEx legacy: {}.", msg);
		AkkaSender.sendToActor(getContext(), IngestActor.NAME, msg, getSender());

		if (redirections.containsKey(msg.getClass())) {
			redirections.get(msg.getClass()).forEach(ref -> {
				ref.tell(msg, getSender());
			});
		}
	}

	private void redirectConnection(Object msg) {
		LOG.debug("IngestEx received: {}.", msg);

		AkkaSender.sendToActor(getContext(), ConnectionManagerActor.NAME, msg, getSender());
		redirectLegacy(msg);
	}

	private void handleIncomingMessage(Object msg) {
		if (redirections.containsKey(msg.getClass())) {
			redirections.get(msg.getClass()).forEach(ref -> {
				ref.forward(msg, getContext());
			});
		} else {
			LOG.warning("IngestEx received non redirected: {}.", msg);
			redirectLegacy(msg);
			// unhandled(msg);
		}
	}
}
