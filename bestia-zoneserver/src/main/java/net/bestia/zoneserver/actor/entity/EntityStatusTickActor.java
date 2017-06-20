package net.bestia.zoneserver.actor.entity;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.model.domain.StatusPoints;
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
@Component
@Scope("prototype")
public class EntityStatusTickActor extends BestiaPeriodicTerminatingActor {

	private final long entityId;
	private final StatusService statusService;

	private float manaIncrement;
	private float healthIncrement;

	@Autowired
	public EntityStatusTickActor(StatusService statusService, long entityId) {

		this.statusService = Objects.requireNonNull(statusService);
		this.entityId = entityId;

		startInterval(StatusService.REGENERATION_TICK_RATE_MS);
	}

	@Override
	protected void onTick() {

		try {

			final float hpTick = statusService.getHealthTick(entityId) + healthIncrement;
			final float manaTick = statusService.getManaTick(entityId) + manaIncrement;

			StatusPoints sp = statusService.getStatusPoints(entityId).orElseThrow(IllegalArgumentException::new);

			if (hpTick > 1) {
				// Update health status.
				final int hpRound = (int) hpTick;
				
				sp.setCurrentHp(sp.getCurrentHp() + hpRound);
				healthIncrement = hpTick - hpRound;
				
			} else {
				healthIncrement = hpTick;
			}

			if (manaTick > 1) {
				// Update mana.
				final int manaRound = (int) manaTick;
				
				sp.setCurrentHp(sp.getCurrentHp() + manaRound);
				healthIncrement = hpTick - manaRound;
				
			} else {
				manaIncrement = manaTick;
			}
			
			statusService.saveStatusPoints(sp);

		} catch (IllegalArgumentException e) {
			// Could not tick regeneration for this entity id. Probably no
			// status component attached.
			// Terminating.
			context().stop(getSelf());
		}
	}

	@Override
	protected void handleMessage(Object message) {
		// no op.
	}
}
