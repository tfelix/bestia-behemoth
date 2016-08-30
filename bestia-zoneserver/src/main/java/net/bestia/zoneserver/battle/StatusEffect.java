package net.bestia.zoneserver.battle;

import net.bestia.model.domain.Attack;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.zone.entity.traits.Attackable;

public interface StatusEffect {

	void onTakeDamage(Attackable receiver, Attackable attacker, Attack atk, Damage dmg);

	void onTick();

	void onApply();

	void onRemove();

	/**
	 * This hook is triggered before damage calculation sets in. This is
	 * basically triggered when it was clear that an attack will hit.
	 */
	void onPreHit(Attackable receiver, Attackable attacker, Attack atk);

}
