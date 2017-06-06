package net.bestia.zoneserver.actor.entity;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.zoneserver.actor.BestiaPeriodicTerminatingActor;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.StatusService;
import net.bestia.zoneserver.entity.component.StatusComponent;

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 * 
 * @author Thomas Felix
 *
 */
public class EntityHpManaRegenActor extends BestiaPeriodicTerminatingActor {
	
	private static final int REGEN_TICK_RATE_MS = 1000;
	
	private long entityId;

	private final EntityService entityService;
	private final StatusService statusService;

	@Autowired
	public EntityHpManaRegenActor(EntityService entityService, StatusService statusService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.statusService = Objects.requireNonNull(statusService);
		
		startInterval(REGEN_TICK_RATE_MS);
	}

	@Override
	protected void onTick() {
		
		Optional<StatusComponent> component = entityService.getComponent(entityId, StatusComponent.class);
		
		if(!component.isPresent()) {
			context().stop(getSelf());
			return;
		}
		
		//statusService.get
		
		// Calculate the regeneration.
		final float manaRegenRate = component.get().getStatusBasedValues().getManaRegenRate();
		final float hpRegenRate = component.get().getStatusBasedValues().getHpRegenRate();
		
		final float manaRegen = manaRegenRate * REGEN_TICK_RATE_MS / 1000;
		final float hpRegen = manaRegenRate * REGEN_TICK_RATE_MS / 1000;
	}

	@Override
	protected void handleMessage(Object message) throws Exception {
		
		if(message instanceof Long) {
			trackEntity((Long) message);
		}

	}

	private void trackEntity(Long entityId) {
		this.entityId = entityId;
	}
}
