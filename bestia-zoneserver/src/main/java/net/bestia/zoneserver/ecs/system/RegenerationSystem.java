package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalIteratingSystem;

import net.bestia.zoneserver.ecs.component.StatusPoints;

/**
 * This system will use the current status points and calculate with the HP and
 * Mana regeneration rate a new value from it.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class RegenerationSystem extends IntervalIteratingSystem {

	private static final int TICK_RATE_MS = 5000;

	private ComponentMapper<StatusPoints> statusPointMapper;

	public RegenerationSystem() {
		super(Aspect.all(StatusPoints.class), TICK_RATE_MS);
		// No op.
	}

	@Override
	protected void process(int entityId) {
		final net.bestia.model.domain.StatusPoints sp = statusPointMapper.get(entityId).statusPoints;
		
		final float hpRegenRate = sp.getHpRegenerationRate();
		final float manaRegenRate = sp.getManaRegenerationRate();

		final int addHp = (int) Math.ceil(hpRegenRate * TICK_RATE_MS / 1000);
		final int addMana = (int) Math.ceil(manaRegenRate * TICK_RATE_MS / 1000);

		sp.addHp(addHp);
		sp.addMana(addMana);
	}

}
