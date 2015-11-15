package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.Mana;
import net.bestia.zoneserver.ecs.component.ManaRegenerationRate;

/**
 * The ManaSystem will use the mana regeneration rate of the entity which backs
 * up
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ManaRegenerationSystem extends IntervalEntityProcessingSystem {

	private static final int TICK_RATE_MS = 5000;

	private ComponentMapper<Mana> manaMapper;
	private ComponentMapper<ManaRegenerationRate> regenRateMapper;

	public ManaRegenerationSystem() {
		super(Aspect.all(Mana.class, ManaRegenerationRate.class), TICK_RATE_MS);
		// No op.
	}

	@Override
	protected void process(Entity e) {

		final Mana currentMana = manaMapper.get(e);
		final ManaRegenerationRate rate = regenRateMapper.get(e);

		int addMana = (int) Math.ceil(rate.rate * TICK_RATE_MS / 1000);
		
		if(currentMana.currentMana >= currentMana.maxMana) {
			currentMana.currentMana = currentMana.maxMana;
			return;
		}

		currentMana.currentMana += addMana;

		// Entity has changed.
		e.edit().create(Changed.class);
	}

}
