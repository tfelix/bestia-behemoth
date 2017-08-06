package net.bestia.zoneserver.actor.entity.component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * Manages the {@link ScriptComponent} for an entity.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptComponentActor extends AbstractActor {

	/**
	 * Adds a script callback actor this this actor.
	 *
	 */
	public static final class AddScriptCallback {

		private final String scriptUid;
		private final int delayMs;

		/**
		 * 
		 * @param scriptUid
		 * @param callbackName
		 * @param delayMs
		 *            Delay between the calls in ms.
		 */
		public AddScriptCallback(String scriptUid, String callbackName, int delayMs) {

			if (delayMs <= 0) {
				throw new IllegalArgumentException("Delay must be bigger then 0.");
			}

			this.scriptUid = Objects.requireNonNull(scriptUid);
			this.delayMs = delayMs;
		}

		public String getScriptUid() {
			return scriptUid;
		}

		public int getDelay() {
			return delayMs;
		}

		@Override
		public String toString() {
			return String.format("AddScriptCallback[scriptUId: %s, delayMs: %d]", getScriptUid(), getDelay());
		}
	}

	/**
	 * Message to remove a previously added script callback actor.
	 *
	 */
	public static final class RemoveScriptCallback {
		private final String scriptUid;

		public RemoveScriptCallback(String scriptUid) {

			this.scriptUid = Objects.requireNonNull(scriptUid);
		}

		public String getScriptUid() {
			return scriptUid;
		}

		@Override
		public String toString() {
			return String.format("AddScriptCallback[scriptUId: %s]", getScriptUid());
		}
	}

	public final static String NAME = "scriptComponent";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final long entityId;

	private final Map<String, ActorRef> scriptActorByUuid = new HashMap<>();
	private final Map<ActorRef, String> uuidByScriptActor = new HashMap<>();

	@Autowired
	public ScriptComponentActor(long entityId) {

		this.entityId = entityId;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AddScriptCallback.class, this::handleAddScript)
				.match(RemoveScriptCallback.class, this::handleRemoveScript)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}

	private void handleAddScript(AddScriptCallback msg) {

		LOG.debug("Periodic script actor added: {}", msg);

		// First we check if we have already an actor with this uuid.
		// If so we advice this actor to change its delay. Removing and adding
		// is async and we dont want to handle the removal of this actor here.
		if (scriptActorByUuid.containsKey(msg.getScriptUid())) {
			scriptActorByUuid.get(msg.getScriptUid()).tell(msg, getSelf());
			return;
		}

		final ActorRef priodicActor = SpringExtension.unnamedActorOf(getContext(), PeriodicScriptActor.class, entityId,
				msg.getDelay(), msg.getScriptUid());

		getContext().watch(priodicActor);
		scriptActorByUuid.put(msg.getScriptUid(), priodicActor);
		uuidByScriptActor.put(priodicActor, msg.getScriptUid());
	}

	private void handleRemoveScript(RemoveScriptCallback msg) {

		LOG.debug("Periodic script actor removed: {}", msg);

		final ActorRef actor = scriptActorByUuid.get(msg.getScriptUid());

		if (actor != null) {
			actor.tell(PoisonPill.getInstance(), getSelf());
		}
	}

	/**
	 * Handle the termination of the periodic movement and remove the actor ref
	 * so we can start a new one.
	 */
	private void handleTerminated(Terminated term) {

		LOG.debug("Periodic script actor terminated: {}", term.getActor().toString());

		final ActorRef termActor = term.actor();
		final String uuid = uuidByScriptActor.get(termActor);

		if (uuid != null) {
			uuidByScriptActor.remove(termActor);
			scriptActorByUuid.remove(uuid);
		}
	}
}
