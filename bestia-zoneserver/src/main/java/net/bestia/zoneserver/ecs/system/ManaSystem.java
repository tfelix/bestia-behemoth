package net.bestia.zoneserver.ecs.system;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.manager.BestiaManager;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

/**
 * The ManaSystem will use the mana regeneration rate of the entity which backs up
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ManaSystem extends IntervalEntityProcessingSystem {

	private static final int TICK_RATE_MS = 5000;

	private ComponentMapper<Bestia> bestiaMapper;

	public ManaSystem() {
		super(Aspect.all(Bestia.class), TICK_RATE_MS);
		// No op.
	}

	@Override
	protected void process(Entity e) {

		final Bestia bestia = bestiaMapper.get(e);
		final BestiaManager bestiaManager = bestia.bestiaManager;

		final int addMana = Math.round((bestiaManager.getManaRegenerationRate() * TICK_RATE_MS / 1000));

		final int curMana = bestiaManager.getStatusPoints().getCurrentMana();
		bestiaManager.getStatusPoints().setCurrentMana(curMana + addMana);
	}

}
