package net.bestia.zoneserver.actor.entity.component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.messages.EntityComponentUpdateMessage;
import net.bestia.zoneserver.actor.SpringExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Manages the {@link ScriptComponent} for an entity.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
public class ScriptComponentActor extends AbstractActor {

	public final static String NAME = "scriptComponent";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final long entityId;
	private final long componentId;
	private final EntityService entityService;

	private final BiMap<String, ActorRef> scriptActors = HashBiMap.create();

	@Autowired
	public ScriptComponentActor(long entityId, long componentId, EntityService entityService) {
		this.entityId = entityId;
		this.componentId = componentId;
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityComponentUpdateMessage.class, this::handleComponentUpdate)
				.match(Terminated.class, this::handleTerminated)
				.build();
	}

	/**
	 * The script component has changed. We try to look into the changes and create or delete all
	 * the actors managing the components.
	 *
	 * @param msg
	 */
	private void handleComponentUpdate(EntityComponentUpdateMessage msg) {
		entityService.getComponent(componentId, ScriptComponent.class).ifPresent(c -> {
			final Set<String> currentActiveUids = scriptActors.keySet();
			final Set<String> componentActiveUids = c.getAllScriptUids();

			componentActiveUids.stream()
					.filter(cuid -> componentActiveUids.contains(cuid))
					.map(c::getCallback)
					.forEach(this::updateActor);

			currentActiveUids.stream()
					.filter(cuid -> !componentActiveUids.contains(cuid))
					.forEach(this::terminateActor);

			componentActiveUids.stream()
					.filter(cuid -> !currentActiveUids.contains(cuid))
					.map(c::getCallback)
					.forEach(this::createActor);
		});
	}

	private void terminateActor(String uid) {
		LOG.debug("Periodic script actor terminating: {}", uid);
		final ActorRef ref = scriptActors.get(uid);
		if (ref != null) {
			ref.tell(PoisonPill.getInstance(), getSelf());
		}
	}

	private void createActor(ScriptComponent.ScriptCallback callback) {
		final ActorRef actor = SpringExtension.unnamedActorOf(getContext(),
				PeriodicScriptActor.class, entityId,
				callback.getIntervalMs(), callback.getScript());

		getContext().watch(actor);
	}

	private void updateActor(ScriptComponent.ScriptCallback callback) {
		final ActorRef ref = scriptActors.get(callback.getUuid());
		if(ref != null) {
			ref.tell(callback, getSelf());
		}
	}

	/**
	 * Handle the termination of the periodic movement and remove the actor ref
	 * so we can start a new one.
	 */
	private void handleTerminated(Terminated term) {
		LOG.debug("Periodic script actor terminated: {}", term.getActor().toString());

		final ActorRef termActor = term.actor();
		scriptActors.inverse().remove(termActor);
	}
}
