package net.bestia.zoneserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.domain.Attack;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.LivingEntity;
import net.bestia.zoneserver.entity.NPCEntity;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.traits.Attackable;

/**
 * This service is used to perform attacks and damage calculation for battle
 * related tasks.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class BattleService {

	private final static Logger LOG = LoggerFactory.getLogger(BattleService.class);
	
	private final EntityService entityService;
	
	@Autowired
	public BattleService(EntityService entityService) {
		
		this.entityService = entityService;
	}

	public boolean canUseAttack(Attackable attacker, int attackId) {
		return true;
	}

	/**
	 * Attacks the ground.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 */
	public void attackGround(Attack usedAttack, Attackable attacker, Point target) {

		// Check if we have valid x and y.

		// Check if target is in sight.
		if (usedAttack.needsLineOfSight() && !hasLineOfSight(attacker.getPosition(), target)) {
			// No line of sight.
			return;
		}

		// Check if target is in range.
		if (usedAttack.getRange() < attacker.getPosition().getDistance(target)) {
			// Out of range.
			return;
		}
	}

	public void attackEntity(Attack usedAttack, Attackable attacker, Attackable defender) {

		// TODO Calculates the damage.
		
		Damage dmg = Damage.getHit(12);
		dmg = defender.takeDamage(dmg);
		
		if(defender.isDead()) {
			
			// Get the EXP and award player.
			int exp = defender.getKilledExp();
			attacker.addExp(exp);
			
			if(!(defender instanceof PlayerEntity)) {
				
				entityService.delete(defender);
				
			}			
		}
	}

	/**
	 * Attacks itself.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 * @param pbe
	 */
	public void attackSelf(AttackUseMessage atkMsg, Attack usedAttack, PlayerEntity pbe) {
		// TODO Auto-generated method stub

	}

	/**
	 * TODO Implementieren.
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	private boolean hasLineOfSight(Point start, Point end) {

		return true;
	}

}
