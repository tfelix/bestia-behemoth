package net.bestia.core.game;

import net.bestia.core.game.battle.Damage;
import net.bestia.core.game.model.Attack;
import net.bestia.core.game.model.Bestia;
import net.bestia.core.game.service.BestiaService;

public interface StatusChanger {
	public void onAttack(BestiaService enemy);
	public void onTick(BestiaService enemy);
	public void onAttack(Attack atk, Damage damage, BestiaService enemy);
	public void onTakeDamage(Damage damage);
	public void onTakeDamage(Damage damage, Bestia enemy);
	public void onRemove();
	public void onStart();
	
}
