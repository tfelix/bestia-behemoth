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
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.battle.Damage;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusValues;
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
	 * Checks if the given entity is able to cast the attack (it knows it) and
	 * also if the current mana is enough to use this skill. This has to take
	 * into account any mana cost reducing status effects.
	 * 
	 * @param attacker
	 * @param attackId
	 * @return
	 */
	public boolean canUseAttack(Entity attacker, int attackId) {
		return true;
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
			LOG.warn("Not entity {} does not have status components.", entity.getId());
			return false;
		}

		if (!entityService.hasComponent(entity, PositionComponent.class)) {
			LOG.warn("Not entity {} does not have position components.", entity.getId());
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
	public void attackGround(Attack usedAttack, Entity attacker, Point target) {

		if (canEntityReceiveDamage(attacker)) {
			return;
		}

		// Check if target is in range.
		if (!isInRange(usedAttack, attacker, target)) {
			return;
		}

		final PositionComponent atkPos = entityService.getComponent(attacker, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new);
		final StatusComponent statusComp = entityService.getComponent(attacker, StatusComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		// Check if target is in sight.
		if (usedAttack.needsLineOfSight() && !hasLineOfSight(atkPos.getPosition(), target)) {
			// No line of sight.
			return;
		}
	}

	/**
	 * Alias for {@link #attackEntity(Attack, Entity, Entity)}.
	 * 
	 * @param attackId
	 * @param atkEntityId
	 * @param defEntityId
	 * @return
	 */
	public Damage attackEntity(int attackId, long atkEntityId, long defEntityId) {
		final Entity attacker = entityService.getEntity(atkEntityId);
		final Entity defender = entityService.getEntity(defEntityId);
		final Attack atk = atkDao.findOne(attackId);
		return attackEntity(atk, attacker, defender);
	}

	/**
	 * This method should be used if a entity directly attacks another entity.
	 * Both entities must posess a {@link StatusComponent}.
	 * 
	 * @param usedAttack
	 * @param attacker
	 * @param defender
	 */
	public Damage attackEntity(Attack usedAttack, Entity attacker, Entity defender) {
		LOG.trace("Entity {} attacks entity {} with {}.", attacker, defender, usedAttack);

		if (!canEntityReceiveDamage(attacker) || !canEntityReceiveDamage(defender)) {
			return null;
		}

		final PositionComponent atkPosition = entityService.getComponent(attacker, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new);
		final PositionComponent defPosition = entityService.getComponent(defender, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		// Check if the target is in range.
		if (!isInRange(usedAttack, attacker, defPosition.getPosition())) {
			return null;
		}

		// Check the line of sight is needed.
		if (usedAttack.needsLineOfSight() && !hasLineOfSight(atkPosition.getPosition(), defPosition.getPosition())) {
			LOG.debug("Attacker has no line of sight.");
			return null;
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

		return receivedDamage;
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

		StatusValues values = statusService.getStatusValues(defender).orElseThrow(IllegalArgumentException::new);

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
		final StatusValues statusValues = statusComp.getValues();

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
	/*
	 * public Damage checkDamage(Damage damage) { return null; }
	 */

	public void killEntity(Entity killed) {
		LOG.trace("Entity {} killed.", killed);
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
	private boolean hasLineOfSight(Point start, Point end) {
		final long x1, x2, y1, y2;

		x1 = Math.min(start.getX(), end.getX());
		x2 = Math.max(start.getX(), end.getX());
		y1 = Math.min(start.getY(), end.getY());
		y2 = Math.max(start.getY(), end.getY());

		final long width = x2 - x1;
		final long height = y2 - y1;

		final Rect bbox = new Rect(x1, y1, width, height);

		final Map map = mapService.getMap(bbox);

		List<Point> lineOfSight = lineOfSight(start, end);

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
	private boolean isInRange(Attack usedAttack, Entity attacker, Point defenderPos) {

		final Point atkPosition = entityService.getComponent(attacker, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new)
				.getPosition();
		final int effectiveRange = getEffectiveSkillRange(usedAttack, attacker);
		return effectiveRange < atkPosition.getDistance(atkPosition);
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
		return 1;
	}
}
