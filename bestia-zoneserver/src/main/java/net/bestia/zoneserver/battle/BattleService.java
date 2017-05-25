package net.bestia.zoneserver.battle;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityServiceContext;
import net.bestia.zoneserver.entity.component.PositionComponent;
import net.bestia.zoneserver.entity.component.StatusComponent;

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
	
	private final EntityServiceContext entityCtx;
	
	@Autowired
	public BattleService(EntityServiceContext entityCtx) {
		
		this.entityCtx = Objects.requireNonNull(entityCtx);
	}

	public boolean canUseAttack(Entity attacker, int attackId) {
		return true;
	}

	/**
	 * Attacks the ground.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 */
	public void attackGround(Attack usedAttack, Entity attacker, Point target) {

		// Check if we have valid x and y.
		if(!entityCtx.getEntity().hasComponent(attacker, StatusComponent.class)) {
			return;
		}
		
		if(!entityCtx.getEntity().hasComponent(attacker, PositionComponent.class)) {
			return;
		}
		
		PositionComponent atkPos = entityCtx.getEntity().getComponent(attacker, PositionComponent.class).get();
		//StatusComponent statusComp = entityCtx.getComponent().getComponent(attacker, StatusComponent.class).get();

		// Check if target is in sight.
		if (usedAttack.needsLineOfSight() && !hasLineOfSight(atkPos.getPosition(), target)) {
			// No line of sight.
			return;
		}

		// Check if target is in range.
		if (usedAttack.getRange() < atkPos.getPosition().getDistance(target)) {
			// Out of range.
			return;
		}
	}

	/*
	public void attackEntity(Attack usedAttack, Entity attacker, Entity defender) {

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
	}*/

	/**
	 * Attacks itself.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 * @param pbe
	 */
	public void attackSelf(AttackUseMessage atkMsg, Attack usedAttack, Entity pbe) {
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
	
	/**
	 * Returns a list with all available attacks.
	 * 
	 * @return A list of all available attacks.
	 */
	//List<Attack> getAttacks();

	/**
	 * This will perform a check damage for reducing it and alter all possible
	 * status effects and then apply the damage to the entity. If its health
	 * sinks below 0 then the {@link #kill()} method will be triggered.
	 * 
	 * @param damage
	 *            The damage to apply to this entity.
	 * @return The reduced damage.
	 */
	//Damage takeDamage(Damage damage);

	/**
	 * Checks the damage and reduces it by resistances or status effects.
	 * Returns the reduced damage the damage can be 0 if the damage was negated
	 * altogether. If there are effects which would be run out because of this
	 * damage then the checking will NOT run them out. It is only a check. Only
	 * applying the damage via {@link #takeDamage(Damage)} will trigger this
	 * removals.
	 * 
	 * @param damage
	 *            The damage to check if taken.
	 * @return Possibly reduced damage or NULL of it was negated completly.
	 */
	//Damage checkDamage(Damage damage);

	/**
	 * Flag if the entity has bean killed. Important for the attack service in
	 * order to perform post death operations.
	 * 
	 * @return TRUE if the entity was killed. FALSE otherwise.
	 */
	//boolean isDead();

	/**
	 * Returns the amount of EXP given if the entity was killed.
	 * 
	 * @return The amount of EXP given by this entity.
	 */
	//int getKilledExp();

}
