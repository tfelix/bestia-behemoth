package net.bestia.zoneserver.battle;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.StatusService;
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

	private final EntityService entityService;
	private final StatusService statusService;

	@Autowired
	public BattleService(EntityService entityService, StatusService statusService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.statusService = Objects.requireNonNull(statusService);
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
		if (!entityService.hasComponent(attacker, StatusComponent.class)) {
			return;
		}

		if (!entityService.hasComponent(attacker, PositionComponent.class)) {
			return;
		}

		PositionComponent atkPos = entityService.getComponent(attacker, PositionComponent.class).get();
		// StatusComponent statusComp =
		// entityCtx.getComponent().getComponent(attacker,
		// StatusComponent.class).get();

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

	/**
	 * This method should be used if a entity directly attacks another entity.
	 * Both entities must posess a {@link StatusComponent}.
	 * 
	 * @param usedAttack
	 * @param attacker
	 * @param defender
	 */
	public void attackEntity(Attack usedAttack, Entity attacker, Entity defender) {
		LOG.trace("Entity {} attacks entity {} with {}.", attacker, defender, usedAttack);

		if (!entityService.hasComponent(attacker, StatusComponent.class)
				|| !entityService.hasComponent(defender, StatusComponent.class)) {
			LOG.warn("Not both entities have status components.");
			return;
		}

		if (!entityService.hasComponent(attacker, PositionComponent.class)
				|| !entityService.hasComponent(defender, PositionComponent.class)) {
			LOG.warn("Not both entities have position components.");
			return;
		}

		final PositionComponent atkPosition = entityService.getComponent(attacker, PositionComponent.class).get();
		final PositionComponent defPosition = entityService.getComponent(defender, PositionComponent.class).get();

		// Check the line of sight.
		if (usedAttack.needsLineOfSight() && !hasLineOfSight(atkPosition.getPosition(), defPosition.getPosition())) {
			LOG.debug("Attacker has no line of sight.");
			return;
		}

		// Check if target is in range.
		if (usedAttack.getRange() < atkPosition.getPosition().getDistance(defPosition.getPosition())) {
			// Out of range.
			LOG.debug("Attack was out of range.");
			return;
		}

		final StatusPoints atkStatus = statusService.getStatusPoints(attacker).get();
		final StatusPoints defStatus = statusService.getStatusPoints(defender).get();

		final StatusBasedValues atkStatusBased = statusService.getStatusBasedValues(attacker).get();
		final StatusBasedValues defStatusBased = statusService.getStatusBasedValues(defender).get();

		// TODO Calculates the damage.

		Damage primaryDamage = Damage.getHit(12);
		Damage receivedDamage = takeDamage(defender, primaryDamage);
	}

	public void takeTrueDamage(Entity defender, Damage trueDamage) {
		// TODO Auto-generated method stub
	}

	public Damage takeDamage(Entity defender, Damage primaryDamage) {
		LOG.trace("Entity {} takes damage: {}.", defender, primaryDamage);
		
		final StatusComponent statusComp = entityService.getComponent(defender, StatusComponent.class)
				.orElseThrow(IllegalArgumentException::new);
		
		final StatusPoints status = statusComp.getStatusPoints();
		
		// TODO Possibly reduce the damge or reflect it etc.
		
		int damage = primaryDamage.getDamage();
		Damage reducedDamage = new Damage(damage, primaryDamage.getType());
		
		if(status.getCurrentHp() < damage) {
			killEntity(defender);
			return reducedDamage;
		}
		
		status.setCurrentHp(status.getCurrentHp() - damage);
		entityService.saveComponent(statusComp);

		return primaryDamage;
	}

	public void killEntity(Entity entity) {
		LOG.trace("Entity {} killed.", entity);
	}

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
	// List<Attack> getAttacks();

	/**
	 * This will perform a check damage for reducing it and alter all possible
	 * status effects and then apply the damage to the entity. If its health
	 * sinks below 0 then the {@link #kill()} method will be triggered.
	 * 
	 * @param damage
	 *            The damage to apply to this entity.
	 * @return The reduced damage.
	 */
	// Damage takeDamage(Damage damage);

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
	// Damage checkDamage(Damage damage);

	/**
	 * Returns the amount of EXP given if the entity was killed.
	 * 
	 * @return The amount of EXP given by this entity.
	 */
	// int getKilledExp();

}
