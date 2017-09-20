package net.bestia.zoneserver.battle;

import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;
import net.bestia.model.domain.Attack;

/**
 * Provides all access to let entities learn attacks.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class AttackService {

	public boolean knowsAttack(Entity entity, int attackId) {
		// TODO Fixme
		return true;
	}

	/**
	 * Checks if the attacks damage or effect is determined by a script or plain
	 * damage calculation.
	 * 
	 * @param atk
	 *            The attack to check.
	 * @return TRUE if the damage is calculated by a script.
	 */
	public boolean hasAttackScript(Attack atk) {
		atk.getBasedStatus() == 
	}
}
