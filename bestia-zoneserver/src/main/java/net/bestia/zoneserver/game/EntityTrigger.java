package net.bestia.zoneserver.game;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Bestia;
import net.bestia.model.service.BestiaManager;
import net.bestia.zoneserver.game.battle.Damage;

/**
 * Triggers events upon entities on the map. All events which can happen to a entity (or better its attached script)
 * will be definied by this interface and implemented by the specialized script.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface EntityTrigger {
	public void onAttack(BestiaManager enemy);

	public void onTick(BestiaManager enemy);

	public void onAttack(Attack atk, Damage damage, BestiaManager enemy);

	public void onTakeDamage(Damage damage);

	public void onTakeDamage(Damage damage, Bestia enemy);

	public void onRemove();

	public void onStart();

}
