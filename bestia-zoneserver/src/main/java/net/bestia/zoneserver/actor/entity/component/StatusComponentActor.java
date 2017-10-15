package net.bestia.zoneserver.actor.entity.component;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.entity.component.StatusService;
import net.bestia.model.domain.ConditionValues;
import net.bestia.zoneserver.actor.BestiaPeriodicTerminatingActor;

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
public class StatusComponentActor extends BestiaPeriodicTerminatingActor {

	public static final String NAME = "statusComponent";
	
	private final long entityId;
	private final StatusService statusService;

	private float manaIncrement;
	private float healthIncrement;

	@Autowired
	public StatusComponentActor(StatusService statusService, long entityId) {

		this.statusService = Objects.requireNonNull(statusService);
		this.entityId = entityId;

		startInterval(StatusService.REGENERATION_TICK_RATE_MS);
	}

	@Override
	protected void onTick() {

		try {

			healthIncrement += statusService.getHealthTick(entityId);
			manaIncrement += statusService.getManaTick(entityId);

			ConditionValues sval = statusService.getConditionalValues(entityId).orElseThrow(IllegalArgumentException::new);

			if (healthIncrement > 1) {
				
				// Update health status.
				final int hpRound = (int) healthIncrement;
				healthIncrement -= hpRound;
				sval.addHealth(hpRound);

			} 

			if (manaIncrement > 1) {
				
				// Update mana.
				final int manaRound = (int) manaIncrement;
				manaIncrement -= manaRound;
				sval.addMana(manaRound);

			}
			
			statusService.save(entityId, sval);

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
