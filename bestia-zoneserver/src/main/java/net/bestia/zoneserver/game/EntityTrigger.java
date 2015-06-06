package net.bestia.zoneserver.game;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.game.battle.Damage;
import net.bestia.zoneserver.game.service.BestiaService;

/**
 * Triggers events upon entities on the map. All events which can happen to a entity (or better its attached script)
 * will be definied by this interface and implemented by the specialized script.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface EntityTrigger {
	public void onAttack(BestiaService enemy);

	public void onTick(BestiaService enemy);

	public void onAttack(Attack atk, Damage damage, BestiaService enemy);

	public void onTakeDamage(Damage damage);

	public void onTakeDamage(Damage damage, Bestia enemy);

	public void onRemove();

	public void onStart();

}
