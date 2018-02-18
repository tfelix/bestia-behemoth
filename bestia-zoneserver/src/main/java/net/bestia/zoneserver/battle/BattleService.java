package net.bestia.zoneserver.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.BattleComponent;
import net.bestia.entity.component.ItemComponent;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackType;
import net.bestia.model.domain.ConditionValues;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.map.MapService;

/**
 * This service is used to perform attacks and damage calculation for battle
 * related tasks.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class BattleService {

	private final static Logger LOG = LoggerFactory.getLogger(BattleService.class);

	private final Random rand = ThreadLocalRandom.current();

	private final AttackDAO atkDao;
	private final EntityService entityService;
	private final MapService mapService;
	private final DamageCalculator damageCalculator;

	@Autowired
	public BattleService(
			EntityService entityService,
			MapService mapService,
			AttackDAO atkDao) {

		this.entityService = Objects.requireNonNull(entityService);
		this.mapService = Objects.requireNonNull(mapService);
		this.atkDao = Objects.requireNonNull(atkDao);
		this.damageCalculator = new DamageCalculator();
	}

	/**
	 * Attacks itself.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 * @param pbe
	 */
	public void attackSelf(AttackUseMessage atkMsg, Attack usedAttack, Entity pbe) {
		// FIXME Reparieren.
		throw new IllegalStateException("Not yet implemented.");
	}

	/**
	 * Performs an attack/skill against a ground target. This will usually spawn
	 * an entity doing AOE damage over time but pre-attack checks have to be
	 * made as well.
	 * 
	 * @param atkId
	 * @param attackerId
	 * @param target
	 *            Point to attack.
	 */
	public void attackGround(int atkId, long attackerId, Point target) {
		// FIXME Reparieren.
		throw new IllegalStateException("Not yet implemented.");
	}

	/**
	 * Alias for {@link #attackEntity(Attack, Entity, Entity)}.
	 * 
	 * @param attackId
	 * @param atkEntityId
	 * @param defEntityId
	 * @return The received damage.
	 */
	public Damage attackEntity(int attackId, long atkEntityId, long defEntityId) {
		final Entity attacker = entityService.getEntity(atkEntityId);
		final Entity defender = entityService.getEntity(defEntityId);
		final Attack atk = atkDao.findOne(attackId);
		return attackEntity(atk, attacker, defender);
	}

	/**
	 * This method should be used if a entity directly attacks another entity.
	 * Both entities must posess a {@link StatusComponent} for the calculation
	 * to take place. If this is missing an {@link IllegalArgumentException}
	 * will be thrown.
	 * 
	 * @param usedAttack
	 * @param attacker
	 * @param defender
	 */
	public Damage attackEntity(Attack usedAttack, Entity attacker, Entity defender) {
		LOG.trace("Entity {} attacks entity {} with {}.", attacker, defender, usedAttack);

		if (isEntityItem(defender)) {
			LOG.debug("Defending entity is an item.");
			return attackItemEntity(usedAttack, attacker, defender);
		} else if (isDefaultEntity(defender)) {
			LOG.debug("Defending entity is a bestia.");
			return attackDefaultEntity(usedAttack, attacker, defender);
		} else {
			LOG.warn("Entity can not receive damage because of missing components.");
			return null;
		}
	}

	/**
	 * Default damage calculation for entity hits.
	 * 
	 * @param usedAttack
	 *            The used attack by the attacker.
	 * @param attacker
	 *            The entity attacking.
	 * @param defender
	 *            The defending entity.
	 * @return The calculated damage object.
	 */
	private Damage attackDefaultEntity(Attack usedAttack, Entity attacker, Entity defender) {

		// Prepare the battle context since this is needed to carry all
		// information.
		final BattleContext battleCtx = createBattleContext(usedAttack, attacker, defender);

		if (!isAttackPossible(battleCtx)) {
			LOG.trace("Attack was not possible.");
			return null;
		}

		// Check if attack hits.

		LOG.trace("Attack did hit.");

		isCriticalHit(battleCtx);

		if (!doesAttackHit(battleCtx)) {
			return Damage.getMiss();
		}

		final Damage primaryDamage = damageCalculator.calculateDamage(battleCtx);
		LOG.trace("Primary damage calculated: {}", primaryDamage);

		// Damage can now be reduced by effects.
		final Damage receivedDamage = takeDamage(attacker, defender, primaryDamage);
		LOG.trace("Entity {} received damage: {}", defender, primaryDamage);

		return receivedDamage;
	}

	/**
	 * Attacks an item entity. The damage calculation differs from the usual
	 * entity.
	 * 
	 * @param usedAttack
	 *            The used attack.
	 * @param attacker
	 *            The attacker entity.
	 * @param defender
	 *            The defender entity.
	 * @return The received damage.
	 */
	private Damage attackItemEntity(Attack usedAttack, Entity attacker, Entity defender) {
		// FIXME Implementieren.
		throw new IllegalStateException("Not yet implemented.");
	}

	private BattleContext createBattleContext(Attack usedAttack, Entity attacker, Entity defender) {
		final DamageVariables dmgVars = getDamageVars(attacker);

		final StatusPoints atkStatus = getStatusPoints(attacker);
		final StatusPoints defStatus = getStatusPoints(defender);

		final StatusBasedValues atkStatusBased = getStatBased(attacker);
		final StatusBasedValues defStatusBased = getStatBased(defender);

		final ConditionValues atkCond = getConditional(attacker);
		final ConditionValues defCond = getConditional(defender);

		final BattleContext.Builder builder = new BattleContext.Builder(usedAttack, attacker, dmgVars);
		builder.setAttackerStatus(atkStatus)
				.setDefenderStatus(defStatus)
				.setAttackerBasedValues(atkStatusBased)
				.setDefenderBasedValues(defStatusBased)
				.setAttackerCondition(atkCond)
				.setDefenderCondition(defCond);

		return builder.build();
	}

	/**
	 * Check if the entity is an item entity. For this it must have a
	 * {@link PositionComponent} and a ItemComponent assigned.
	 *
	 * @return TRUE if this is an item entity.
	 */
	private boolean isEntityItem(Entity entity) {
		return entityService.hasComponent(entity, PositionComponent.class)
				&& entityService.hasComponent(entity, ItemComponent.class);
	}

	/**
	 * It must be checked if an entity is eligible for receiving damage. This
	 * means that an {@link StatusComponent} as well as a
	 * {@link PositionComponent} must be present.
	 * 
	 * @return TRUE if the entity is abtle to receive damage. FALSE otherwise.
	 */
	private boolean isDefaultEntity(Entity entity) {
		// Check if we have valid x and y.
		if (!entityService.hasComponent(entity, StatusComponent.class)) {
			LOG.warn("Entity {} does not have status component.", entity);
			return false;
		}

		if (!entityService.hasComponent(entity, PositionComponent.class)) {
			LOG.warn("Entity {} does not have position component.", entity);
			return false;
		}

		if (!entityService.hasComponent(entity, LevelComponent.class)) {
			LOG.warn("Entity {} does not have level component.", entity);
			return false;
		}

		return true;
	}

	/**
	 * Checks if the attack is able to hit its target.
	 * 
	 * @return TRUE if the attack hits the target. FALSE otherwise.
	 */
	private boolean doesAttackHit(BattleContext battleCtx) {

		final Attack attack = battleCtx.getUsedAttack();
		final Entity attacker = battleCtx.getAttacker();
		final Entity defender = battleCtx.getDefender();
		final DamageVariables dmgVar = battleCtx.getDamageVariables();

		LOG.trace("Calculate hit.");

		final AttackType atkType = attack.getType();

		if (atkType == AttackType.MELEE_MAGIC
				|| atkType == AttackType.RANGED_MAGIC
				|| atkType == AttackType.NO_DAMAGE) {
			LOG.trace("Non physical attacks always hits.");
			return true;
		}

		final StatusBasedValues atkStatBased = getStatBased(attacker);
		final StatusBasedValues defStatBased = getStatBased(defender);

		float hitrate = 0.5f * atkStatBased.getHitrate() / defStatBased.getDodge();

		if (dmgVar.isCriticalHit()) {
			hitrate *= 3;
		}

		hitrate = BattleUtil.between(0.05f, 1, hitrate);

		LOG.trace("Hit chance: {}", hitrate);

		if (rand.nextFloat() < hitrate) {
			LOG.trace("Attack was hit.");
			return true;
		} else {
			LOG.trace("Attack did not hit.");
			return false;
		}
	}

	/**
	 * Checks if an attack is even possible at all. Attacks only succeed if
	 * certain preconditions like line of sight, ammo and mana are not missing.
	 * 
	 * @return TRUE if the attack action is possible. FALSE otherwise.
	 */
	private boolean isAttackPossible(BattleContext battleCtx) {
		return isInRange(battleCtx)
				&& hasLineOfSight(battleCtx)
				&& hasAttackerEnoughMana(battleCtx)
				&& hasAmmo(battleCtx);
	}

	/**
	 * Checks if a given attack is in range for a target position. It is
	 * important to ask the attached entity scripts as these can alter the
	 * effective range.
	 *
	 * @return TRUE if the attack is in range. FALSE otherwise.
	 */
	private boolean isInRange(BattleContext battleCtx) {

		final Point atkPosition = getPosition(battleCtx.getAttacker());
		final Point defPosition = getPosition(battleCtx.getDefender());

		final int effectiveRange = getEffectiveSkillRange(battleCtx.getUsedAttack(), battleCtx.getAttacker());

		LOG.trace("Effective attack range: {}", effectiveRange);

		return effectiveRange >= atkPosition.getDistance(defPosition);
	}

	/**
	 * Checks if there is a direct line of sight between the two points. This
	 * does not only take static map features into account but also dynamic
	 * effects like entities which might block the direct line of sight.
	 *
	 * @return Returns TRUE if there is a direct line of sight. FALSE if there
	 *         is no direct line of sight.
	 */
	private boolean hasLineOfSight(BattleContext battleCtx) {

		final Attack attack = battleCtx.getUsedAttack();
		final Entity attacker = battleCtx.getAttacker();
		final Entity defender = battleCtx.getDefender();

		if (!attack.needsLineOfSight()) {
			LOG.trace("Attack does not need los.");
			return true;
		}

		final Point start = entityService.getComponent(attacker, PositionComponent.class).get().getPosition();
		final Point end = entityService.getComponent(defender, PositionComponent.class).get().getPosition();

		final long x1, x2, y1, y2;
		x1 = Math.min(start.getX(), end.getX());
		x2 = Math.max(start.getX(), end.getX());
		y1 = Math.min(start.getY(), end.getY());
		y2 = Math.max(start.getY(), end.getY());

		final long width = x2 - x1;
		final long height = y2 - y1;

		final Rect bbox = new Rect(x1, y1, width, height);

		final Map map = mapService.getMap(bbox);

		final List<Point> lineOfSight = lineOfSight(start, end);

		final boolean doesMapBlock = lineOfSight.stream().anyMatch(map::blocksSight);

		final Set<Entity> blockingEntities = entityService.getCollidingEntities(bbox);
		final boolean doesEntityBlock = blockingEntities.stream().anyMatch(entity -> {
			final Optional<PositionComponent> pos = entityService.getComponent(entity, PositionComponent.class);

			if (!pos.isPresent()) {
				return false;
			}

			final CollisionShape shape = pos.get().getShape();

			return lineOfSight.stream().anyMatch(shape::collide);
		});

		final boolean hasLos = !doesMapBlock && !doesEntityBlock;
		LOG.trace("Entity has line of sight: {}", hasLos);
		return hasLos;
	}

	/**
	 * Checks if the attack performs a critical hit onto the target. The outcome
	 * of the critical hit check is then saved into damage variables.
	 * 
	 */
	private boolean isCriticalHit(BattleContext battleCtx) {

		final Attack attack = battleCtx.getUsedAttack();
		final Entity attacker = battleCtx.getAttacker();
		final Entity defender = battleCtx.getDefender();
		final DamageVariables dmgVars = battleCtx.getDamageVariables();

		LOG.trace("Calculating: criticalHit");

		if (attack.isMagic() || attack.getType() == AttackType.NO_DAMAGE) {
			LOG.trace("Attack is magic. Can not hit critical.");
			dmgVars.setCriticalHit(false);
			return false;
		}

		final int atkLv = getLevel(attacker);
		final int defLv = getLevel(defender);

		final StatusPoints atkStatus = getStatusPoints(attacker);
		final StatusPoints defStatus = getStatusPoints(defender);

		final float atkDex = atkStatus.getDexterity();
		final float defDex = defStatus.getDexterity();

		final float atkAgi = atkStatus.getAgility();
		final float defAgi = defStatus.getAgility();

		float crit = (0.02f + ((atkLv / defLv) / 5)
				+ ((atkDex / defDex) / 2)
				+ ((atkAgi / defAgi) / 2))
				* dmgVars.getCriticalChanceMod();

		crit = BattleUtil.between(0.01f, 0.95f, crit);

		LOG.trace("Crit chance: {}", crit);

		if (rand.nextFloat() < crit) {
			LOG.trace("Attack was critical hit.");
			dmgVars.setCriticalHit(true);
			return true;
		} else {
			LOG.trace("Attack did not critical hit.");
			dmgVars.setCriticalHit(false);
			return false;
		}
	}

	/**
	 * Calculates the needed mana for an attack. Mana cost can be reduced by
	 * effects or scripts.
	 * 
	 * @param battleCtx
	 *            The {@link BattleContext}.
	 * @return The actual mana costs for this attack.
	 */
	private int getNeededMana(BattleContext battleCtx) {
		final Attack attack = battleCtx.getUsedAttack();
		final DamageVariables dmgVars = battleCtx.getDamageVariables();

		final int neededMana = (int) Math.ceil(attack.getManaCost() * dmgVars.getNeededManaMod());
		LOG.trace("Needed mana: {}/{}", neededMana, attack.getManaCost());
		return neededMana;
	}

	/**
	 * Check if the entity has the mana needed for the attack.
	 * 
	 * @return TRUE if the entity has enough mana to perform the attack. FALSE
	 *         otherwise.
	 */
	private boolean hasAttackerEnoughMana(BattleContext battleCtx) {
		final int neededMana = getNeededMana(battleCtx);
		return battleCtx.getAttackerCondition().getCurrentMana() >= neededMana;
	}

	private boolean hasAmmo(BattleContext battleCtx) {
		LOG.warn("hasAmmo currently not implemented.");
		return true;
	}

	/**
	 * Gets the current damage variables of an entity counting for itself. This
	 * usually boosts the own values for more damage. This function will also
	 * invoke all the scripts currently attached to the entity which might alter
	 * the damage var.
	 *
	 * @param e
	 *            The entity to get the damage vars for.
	 * @return The current damage vars of the entity.
	 */
	private DamageVariables getDamageVars(Entity e) {
		// FIXME Implementieren.
		return new DamageVariables();
	}

	/**
	 * The true damage is applied directly to the entity without further
	 * reducing the damage via armor.
	 * 
	 * @param defender
	 * @param trueDamage
	 */
	public void takeTrueDamage(Entity defender, Damage trueDamage) {

		final int damage = trueDamage.getDamage();

		entityService.getComponent(defender, StatusComponent.class).ifPresent(statusComp -> {

			statusComp.getConditionValues().addHealth(-damage);

			if (statusComp.getConditionValues().getCurrentHealth() == 0) {
				killEntity(defender);
			}

			entityService.updateComponent(statusComp);
		});
	}

	/**
	 * This will perform a check damage for reducing it and alter all possible
	 * status effects and then apply the damage to the entity. If its health
	 * sinks below 0 then the {@link #killEntity(Entity)} method will be triggered. It will
	 * also trigger any attached script trigger for received damage this is
	 * onTakeDamage and onApplyDamage.
	 * 
	 * @param primaryDamage
	 *            The damage to apply to this entity.
	 * @return The actually applied damage.
	 */
	public Damage takeDamage(Entity attacker, Entity defender, Damage primaryDamage) {
		LOG.trace("Entity {} takes damage: {}.", defender, primaryDamage);

		final StatusComponent statusComp = entityService.getComponent(defender, StatusComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		// TODO Possibly reduce the damage via effects or scripts.
		int damage = primaryDamage.getDamage();
		final Damage reducedDamage = new Damage(damage, primaryDamage.getType());

		// Hit the entity and add the origin entity into the list of received
		// damage dealers.
		final BattleComponent battleComp = entityService.getComponentOrCreate(defender, BattleComponent.class);
		battleComp.addDamageReceived(attacker.getId(), damage);
		entityService.updateComponent(battleComp);

		final ConditionValues condValues = statusComp.getConditionValues();
		condValues.addHealth(-damage);
		entityService.updateComponent(statusComp);
		
		if (condValues.getCurrentHealth() == 0) {
			killEntity(defender);
		}
		
		return reducedDamage;
	}

	/**
	 * Entity received damage with not entity as origin. Just plain damage.
	 * 
	 * @param defender
	 * @param damage
	 * @return
	 */
	public Damage takeDamage(Entity defender, int damage) {
		final Damage dmg = Damage.getHit(damage);
		return takeDamage(null, defender, dmg);
	}

	public void killEntity(Entity killed) {
		LOG.debug("Entity {} killed.", killed);

		// Entity will play death animation.

		// If its player entity then set
	}

	/**
	 * Calculates a list of points which lie under the given line of sight. This
	 * uses Bresenham line algorithm.
	 * 
	 * @param start
	 *            Starting point.
	 * @param end
	 *            End point.
	 * @return A list of points which are under the
	 */
	private List<Point> lineOfSight(Point start, Point end) {
		final List<Point> result = new ArrayList<>();

		long dx = end.getX() - start.getX();
		long dy = end.getY() - start.getY();
		long D = 2 * dy - dx;
		long y = start.getY();

		for (long x = start.getX(); x <= end.getX(); x++) {

			result.add(new Point(x, y));

			if (D > 0) {
				y = y + 1;
				D = D - 2 * dx;
			}

			D = D + 2 * dy;
		}

		return result;
	}

	/**
	 * Calculates the effective range of the attack. A skill range can be
	 * altered by an equipment or a buff for example.
	 */
	private int getEffectiveSkillRange(Attack usedAttack, Entity user) {
		return usedAttack.getRange();
	}

	/**
	 * @return Current position of the entity.
	 */
	private Point getPosition(Entity e) {
		return entityService.getComponent(e, PositionComponent.class)
				.map(PositionComponent::getPosition)
				.orElse(new Point(0, 0));
	}

	/**
	 * @return The {@link StatusPoints} of a entity.
	 */
	private StatusPoints getStatusPoints(Entity e) {
		return entityService.getComponent(e, StatusComponent.class)
				.map(StatusComponent::getStatusPoints)
				.orElse(new StatusPointsImpl());
	}

	/**
	 * @return {@link StatusBasedValues} of the entity.
	 */
	private StatusBasedValues getStatBased(Entity e) {
		return entityService.getComponent(e, StatusComponent.class)
				.map(StatusComponent::getStatusBasedValues)
				.orElseThrow(IllegalArgumentException::new);
	}

	/**
	 * @return {@link StatusBasedValues} of the entity.
	 */
	private ConditionValues getConditional(Entity e) {
		return entityService.getComponent(e, StatusComponent.class)
				.map(StatusComponent::getConditionValues)
				.orElseThrow(IllegalArgumentException::new);
	}

	/**
	 * @return The level of the entity.
	 */
	private int getLevel(Entity e) {
		return entityService.getComponent(e, LevelComponent.class)
				.map(LevelComponent::getLevel)
				.orElse(1);
	}
}
