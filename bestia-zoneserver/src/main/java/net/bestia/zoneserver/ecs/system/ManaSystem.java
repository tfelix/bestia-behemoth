package net.bestia.zoneserver.ecs.system;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.Mana;
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

	private ComponentMapper<Mana> manaMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Changable> changableMapper;

	@SuppressWarnings("unchecked")
	public ManaSystem() {
		super(Aspect.all(Mana.class, Bestia.class), TICK_RATE_MS);
		// No op.
	}

	@Override
	protected void process(Entity e) {

		final Mana mana = manaMapper.get(e);
		final Bestia bestia = bestiaMapper.get(e);
		final BestiaManager bestiaManager = bestia.bestiaManager;

		final int addMana = Math.round((bestiaManager.getManaRegenerationRate() * TICK_RATE_MS / 1000));

		mana.curMana += addMana;
		if (mana.curMana > mana.maxMana) {
			mana.curMana = mana.maxMana;
		}

		// Flag the entity as changed if it has this component.
		// Das hier vielleicht über eine Art Queue lösen: Geänderte Objekte in die Queue schieben welche dann am Ende
		// eines ECS runs gesendet werden.
		final Changable changable = changableMapper.getSafe(e);

		if (changable == null) {
			return;
		}

		changable.setChanged();
	}

}
