package net.bestia.zoneserver.actor.entity;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.zoneserver.actor.BestiaPeriodicTerminatingActor;
import net.bestia.zoneserver.entity.StatusService;

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 * 
 * @author Thomas Felix
 *
 */
public class EntityHpManaRegenActor extends BestiaPeriodicTerminatingActor {

	private long entityId;

	private final StatusService statusService;

	@Autowired
	public EntityHpManaRegenActor(StatusService statusService) {

		this.statusService = Objects.requireNonNull(statusService);

		startInterval(StatusService.REGENERATION_TICK_RATE_MS);
	}

	@Override
	protected void onTick() {

		try {
			statusService.tickRegeneration(entityId);
		} catch (IllegalArgumentException e) {
			// Could not tick regeneration for this entity id. Terminating.
			context().stop(getSelf());
		}
	}

	@Override
	protected void handleMessage(Object message) throws Exception {

		if (message instanceof Long) {
			trackEntity((Long) message);
		}

	}

	private void trackEntity(Long entityId) {
		this.entityId = entityId;
	}
}
