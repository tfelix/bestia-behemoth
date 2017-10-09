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
import net.bestia.entity.StatusService;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackType;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.domain.ConditionValues;
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
	private final StatusService statusService;
	private final MapService mapService;

	@Autowired
	public BattleService(
			EntityService entityService,
			StatusService statusService,
			MapService mapService,
			AttackDAO atkDao) {

		this.entityService = Objects.requireNonNull(entityService);
		this.statusService = Objects.requireNonNull(statusService);
		this.mapService = Objects.requireNonNull(mapService);
		this.atkDao = Objects.requireNonNull(atkDao);
	}

	/**
	 * It must be checked if an entity is eligible for receiving damage. This
	 * means that an {@link StatusComponent} as well as a
	 * {@link PositionComponent} must be present.
	 * 
	 * @return TRUE if the entity is abtle to receive damage. FALSE otherwise.
	 */
	private boolean canEntityReceiveDamage(Entity entity) {
		// Check if we have valid x and y.
		if (!entityService.hasComponent(entity, StatusComponent.class)) {
			LOG.warn("Not entity {} does not have status component.", entity.getId());
			return false;
		}

		if (!entityService.hasComponent(entity, PositionComponent.class)) {
			LOG.warn("Not entity {} does not have position component.", entity.getId());
			return false;
		}

		if (!entityService.hasComponent(entity, LevelComponent.class)) {
			LOG.warn("Not entity {} does not have level component.", entity.getId());
			return false;
		}

		return true;
	}

	/**
	 * Performs an attack/skill against a ground target.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 */
	public void attackGround(int atkId, long attackerId, Point target) {

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

		if (!canEntityReceiveDamage(attacker)) {
			LOG.warn("Attacker entity can not receive damage. Missing StatusComponent or PositionComponent.");
			return null;
		}

		if (!canEntityReceiveDamage(defender)) {
			LOG.warn("Defender entity can not receive damage. Missing StatusComponent or PositionComponent.");
			return null;
		}

		// Create the damage variables.
		final DamageVariables dmgVars = null;

		if (!isAttackPossible(usedAttack, attacker, defender, dmgVars)) {
			LOG.trace("Attack was not possible.");
			return null;
		}

		checkCriticalHit(usedAttack, attacker, defender, dmgVars);

		if (!doesAttackHit(usedAttack, attacker, defender, dmgVars)) {
			return Damage.getMiss();
		}

		// Prepare the battle context.
		final StatusPoints atkStatus = statusService.getStatusPoints(attacker).get();
		final StatusPoints defStatus = statusService.getStatusPoints(defender).get();

		final StatusBasedValues atkStatusBased = statusService.getStatusBasedValues(attacker).get();
		final StatusBasedValues defStatusBased = statusService.getStatusBasedValues(defender).get();

		Damage primaryDamage;
		BattleContext.Builder builder = new BattleContext.Builder(usedAttack, attacker);
		builder.setAttackerStatus(atkStatus)
				.setDefenderStatus(defStatus)
				.setAttackerBasedValues(atkStatusBased)
				.setDefenderBasedValues(defStatusBased);

		final BattleContext battleCtx = builder.build();

		// Check if this was a skill or a normal attack.
		if (usedAttack.getId() == Attack.DEFAULT_MELEE_ATTACK_ID) {
			primaryDamage = calculateMeleeDamage(battleCtx);
		} else if (usedAttack.getId() == Attack.DEFAULT_RANGE_ATTACK_ID) {
			primaryDamage = calculateRangePhysicalDamage(battleCtx);
		} else {
			primaryDamage = calculateRangePhysicalDamage(battleCtx);
		}

		Damage receivedDamage = takeDamage(defender, primaryDamage);

		// Save both entities since attacker has lost mana and defender HP and
		// possibly more.

		return receivedDamage;
	}

	/**
	 * Checks if the attack performs a critical hit onto the target. The outcome
	 * of the critical hit check is then saved into dmgVars.
	 * 
	 * @param attack
	 * @param attacker
	 * @param defender
	 * @param dmgVars
	 */
	private void checkCriticalHit(Attack attack, Entity attacker, Entity defender, DamageVariables dmgVars) {

		LOG.trace("Calculating: criticalHit");

		if (attack.getType() == AttackType.MELEE_MAGIC
				|| attack.getType() == AttackType.RANGED_MAGIC
				|| attack.getType() == AttackType.NO_DAMAGE) {
			LOG.trace("Attack is magic. Can not hit critical.");
			dmgVars.setCriticalHit(false);
			return;
		}

		final int atkLv = getLevel(attacker);
		final int defLv = getLevel(defender);

		final StatusPoints atkStatus = getStatusPoints(attacker);
		final StatusPoints defStatus = getStatusPoints(defender);

		final float atkDex = atkStatus.getDexterity();
		final float defDex = defStatus.getDexterity();

		final float atkAgi = atkStatus.getAgility();
		final float defAgi = defStatus.getAgility();

		float crit = 0.02f * (atkLv / defLv) / 3 * (atkDex / defDex) / 2 * (atkAgi / defAgi) * dmgVars.getCriticalMod();

		crit = between(0.01f, 0.95f, crit);

		LOG.trace("Crit chance: {}", crit);

		if (rand.nextFloat() < crit) {
			LOG.trace("Attack was critical hit.");
			dmgVars.setCriticalHit(true);
		} else {
			LOG.trace("Attack did not critical hit.");
			dmgVars.setCriticalHit(false);
		}
	}

	/**
	 * Checks if the attack is able to hit its target.
	 * 
	 * @return TRUE if the attack hits the target. FALSE otherwise.
	 */
	private boolean doesAttackHit(Attack attack, Entity attacker, Entity defender, DamageVariables dmgVars) {

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

		if (dmgVars.isCriticalHit()) {
			hitrate *= 3;
		}

		hitrate = between(0.05f, 1, hitrate);

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
	 * Checks if an attack is possible of if certain preconditions like line of
	 * sight, ammo and mana are missing.
	 * 
	 * @param dmgVars
	 * 
	 * @return TRUE if the attack action is possible. FALSE otherwise.
	 */
	private boolean isAttackPossible(Attack attack, Entity attacker, Entity defender, DamageVariables dmgVars) {
		return isInRange(attack, attacker, defender)
				&& hasLineOfSight(attack, attacker, defender)
				&& hasMana(attack, attacker, dmgVars)
				&& hasAmmo(attack, attacker);
	}

	private boolean hasAmmo(Attack attack, Entity attacker) {
		LOG.warn("hasAmmo currently not implemented.");
		return true;
	}

	/**
	 * Check if the entity has the mana needed for the attack. The mana is
	 * subtracted from the entity.
	 * 
	 * @param attacker
	 * @param attack
	 * @param dmgVars
	 * @return TRUE if the entity has enough mana to perform the attack. FALSE
	 *         otherwise.
	 */
	private boolean hasMana(Attack attack, Entity attacker, DamageVariables dmgVars) {

		final int neededMana = (int) Math.ceil(attack.getManaCost() * dmgVars.getNeededManaMod());

		final StatusComponent statusComp = entityService.getComponent(attacker, StatusComponent.class).get();
		final int currentMana = statusComp.getConditionValues().getCurrentMana();

		LOG.trace("Needed mana: {}/{}", neededMana, currentMana);

		if (currentMana >= neededMana) {
			statusComp.getConditionValues().setCurrentMana(currentMana - neededMana);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Checks if there is a direct line of sight between the two points. This
	 * does not only take static map features into account but also dynamic
	 * effects like entities which might block the direct line of sight.
	 * 
	 * @param start
	 *            Start point of the line of sight.
	 * @param end
	 *            The end point of the line of sight.
	 * @return Returns TRUE if there is a direct line of sight. FALSE if there
	 *         is no direct line of sight.
	 */
	private boolean hasLineOfSight(Attack attack, Entity attacker, Entity defender) {
		LOG.trace("hasLineOfSight");

		if (!attack.needsLineOfSight()) {
			LOG.trace("Attack does not need los.");
			return false;
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

		final boolean doesMapBlock = lineOfSight.stream().filter(map::blocksSight).findAny().isPresent();

		final Set<Entity> blockingEntities = entityService.getCollidingEntities(bbox);
		final boolean doesEntityBlock = blockingEntities.stream().anyMatch(entity -> {
			final Optional<PositionComponent> pos = entityService.getComponent(entity, PositionComponent.class);

			if (!pos.isPresent()) {
				return false;
			}

			final CollisionShape shape = pos.get().getShape();

			return lineOfSight.stream().anyMatch(los -> {
				return shape.collide(los);
			});
		});

		return !doesMapBlock && !doesEntityBlock;
	}

	private Damage calculateRangePhysicalDamage(BattleContext battleCtx) {
		return Damage.getHit(10);
	}

	/**
	 * This calculates the taken battle damage. Currently this is only a
	 * placeholder until the real damage formula is invented.
	 * 
	 * Please not that this method ONLY calculates the damage. If the attack is
	 * controlled by a script this wont get checked by this method anymore. Only
	 * raw damage calculation is performed.
	 * 
	 * @param battleCtx
	 * @return The calculated damage by this attack.
	 */
	private Damage calculateMeleeDamage(BattleContext battleCtx) {

		final int atk = battleCtx.getAttackerStatusPoints().getStrength();
		final int def = battleCtx.getDefenderStatusPoints().getVitality();

		final float defenseMod = Math.min(0.95f, 1 - battleCtx.getDefenderStatusPoints().getDefense() / 100.f);

		float dmg = (atk + (5 * rand.nextFloat())) * defenseMod - def;

		return Damage.getHit((int) dmg);
	}

	/**
	 * Attacks itself.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 * @param pbe
	 */
	public void attackSelf(AttackUseMessage atkMsg, Attack usedAttack, Entity pbe) {
		// TODO Coden.
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

		ConditionValues values = statusService.getStatusValues(defender).orElseThrow(IllegalArgumentException::new);

		if (values.getCurrentHealth() <= damage) {
			killEntity(defender);
			return;
		}

		values.addHealth(-damage);
		statusService.save(defender, values);
	}

	/**
	 * This will perform a check damage for reducing it and alter all possible
	 * status effects and then apply the damage to the entity. If its health
	 * sinks below 0 then the {@link #kill()} method will be triggered. It will
	 * also trigger any attached script trigger for received damage this is
	 * onTakeDamage and onApplyDamage.
	 * 
	 * @param damage
	 *            The damage to apply to this entity.
	 * @return The actually applied damage.
	 */
	public Damage takeDamage(Entity defender, Damage primaryDamage) {
		LOG.trace("Entity {} takes damage: {}.", defender, primaryDamage);

		final StatusComponent statusComp = entityService.getComponent(defender, StatusComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final StatusPoints status = statusComp.getStatusPoints();
		final ConditionValues statusValues = statusComp.getConditionValues();

		// TODO Possibly reduce the damge or reflect it etc.

		int damage = primaryDamage.getDamage();
		Damage reducedDamage = new Damage(damage, primaryDamage.getType());

		if (statusValues.getCurrentHealth() < damage) {
			killEntity(defender);
			return reducedDamage;
		}

		statusValues.addHealth(-damage);
		entityService.updateComponent(statusComp);

		return primaryDamage;
	}

	public void killEntity(Entity killed) {
		LOG.trace("Entity {} killed.", killed);
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
	 * Checks if a given attack is in range for a target position. It is
	 * important to ask the attached entity scripts as these can alter the
	 * effective range.
	 * 
	 * @param usedAttack
	 *            The attack beeing used.
	 * @param attacker
	 *            The attacker who uses the attack.
	 * @param defenderPos
	 *            The target position where the attack is directed.
	 * @return TRUE if the attack is in range. FALSE otherwise.
	 */
	private boolean isInRange(Attack attack, Entity attacker, Entity defender) {

		final Point atkPosition = entityService.getComponent(attacker, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new)
				.getPosition();

		final Point defPosition = entityService.getComponent(defender, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new)
				.getPosition();

		final int effectiveRange = getEffectiveSkillRange(attack, attacker);

		LOG.trace("Effective attack range: {}", effectiveRange);

		return effectiveRange <= atkPosition.getDistance(defPosition);
	}

	/**
	 * Calculates the effective range of the attack. A skill range can be
	 * altered by an equipment or a buff for example.
	 * 
	 * @param usedAttack
	 * @param user
	 * @return
	 */
	private int getEffectiveSkillRange(Attack usedAttack, Entity user) {
		return usedAttack.getRange();
	}

	private Point getPosition(Entity e) {
		return entityService.getComponent(e, PositionComponent.class)
				.map(p -> p.getPosition())
				.orElse(new Point(0, 0));
	}

	/**
	 * @param e
	 *            The entity.
	 * @return The level of the entity.
	 */
	private int getLevel(Entity e) {
		return entityService.getComponent(e, LevelComponent.class)
				.map(lv -> lv.getLevel())
				.orElse(1);
	}

	/**
	 * @param attacker
	 * @return The {@link StatusPoints} of a entity.
	 */
	private StatusPoints getStatusPoints(Entity e) {
		return entityService.getComponent(e, StatusComponent.class)
				.map(c -> c.getStatusPoints())
				.orElse(new StatusPointsImpl());
	}

	private StatusBasedValues getStatBased(Entity e) {
		return entityService.getComponent(e, StatusComponent.class)
				.map(StatusComponent::getStatusBasedValues)
				.orElseThrow(IllegalArgumentException::new);
	}

	private float between(float min, float max, float val) {
		return Math.max(min, Math.min(max, val));
	}
}
