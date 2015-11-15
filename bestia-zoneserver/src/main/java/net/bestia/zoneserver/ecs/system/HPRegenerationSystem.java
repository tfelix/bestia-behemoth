package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.HP;
import net.bestia.zoneserver.ecs.component.HPRegenerationRate;

/**
 * The ManaSystem will use the mana regeneration rate of the entity which backs
 * up
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class HPRegenerationSystem extends IntervalEntityProcessingSystem {

	private static final int TICK_RATE_MS = 5000;

	private ComponentMapper<HP> hpMapper;
	private ComponentMapper<HPRegenerationRate> regenRateMapper;

	public HPRegenerationSystem() {
		super(Aspect.all(HP.class, HPRegenerationRate.class), TICK_RATE_MS);
		// No op.
	}

	@Override
	protected void process(Entity e) {

		final HP currentHp = hpMapper.get(e);
		final HPRegenerationRate rate = regenRateMapper.get(e);

		int addHp = (int) Math.ceil(rate.rate * TICK_RATE_MS / 1000);
		
		if(currentHp.currentHP >= currentHp.maxHP) {
			currentHp.currentHP = currentHp.maxHP;
			return;
		}

		currentHp.currentHP += addHp;

		// Entity has changed.
		e.edit().create(Changed.class);
	}

}
