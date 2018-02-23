package net.bestia.zoneserver.battle;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.AttackListComponent;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Provides all access to let entities learn attacks.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class AttackService {

	private static final Logger LOG = LoggerFactory.getLogger(AttackService.class);

	private final AttackDAO attackDao;
	private final EntityService entityService;

	@Autowired
	public AttackService(
			EntityService entityService,
			AttackDAO atkDao) {

		this.entityService = Objects.requireNonNull(entityService);
		this.attackDao = Objects.requireNonNull(atkDao);
	}

	/**
	 * Teaches the given entity the given attack via its ID. In order for the
	 * entity to learn the attack it must have a {@link StatusComponent} aswell
	 * as a {@link PositionComponent}. If it has not a
	 * {@link AttackListComponent} this component will be added.
	 * 
	 * @param entityId
	 *            The entity to learn the attack.
	 * @param attackId
	 *            The attack to learn.
	 */
	public void learnAttack(long entityId, int attackId) {
		LOG.debug("Entity {} learns attack {}.");

	}

	/**
	 * Checks if the bestia knows this attack.
	 */
	public boolean knowsAttack(long entityId, int attackId) {
		final Attack attack = attackDao.findOne(attackId);
		final Entity attacker = entityService.getEntity(attackId);
		return knowsAttack(attacker, attack);
	}

	/**
	 * This method checks if the bestia has learned the attack and can use it.
	 * In order to do so it uses the {@link AttackListComponent}. If the entity
	 * does not own this component false is returned.
	 * 
	 * @param entity
	 * @param attack
	 * @return TRUE if the entity knows the attack FALSE otherwise.
	 */
	public boolean knowsAttack(Entity entity, Attack attack) {

		final Optional<AttackListComponent> attacks = entityService.getComponent(entity, AttackListComponent.class);

		if (!attacks.isPresent()) {
			return false;
		}

		return attacks.get().knowsAttack(attack.getId());
	}

	/**
	 * Checks if the given entity is able to cast the attack (it knows it) and
	 * also if the current mana is enough to use this skill. This has to take
	 * into account any mana cost reducing status effects.
	 *
	 * @return TRUE of the entity can now use this skill/attack.
	 */
	public boolean canUseAttack(long attackerId, int attackId) {
		
		// TODO Check line of sight.

		final Attack attack = attackDao.findOne(attackId);
		final Entity attacker = entityService.getEntity(attackId);

		return hasAllBattleComponents(attacker) && knowsAttack(attacker, attack) && hasManaForAttack(attacker, attack);
	}

	private boolean hasManaForAttack(Entity entity, Attack attack) {

		return false;
	}

	private boolean hasAllBattleComponents(Entity entity) {
		if (entity == null) {
			return false;
		}
		return entityService.hasComponent(entity, PositionComponent.class)
				&& entityService.hasComponent(entity, StatusComponent.class)
				&& entityService.hasComponent(entity, LevelComponent.class);
	}

}
