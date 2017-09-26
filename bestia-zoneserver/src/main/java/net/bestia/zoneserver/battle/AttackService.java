package net.bestia.zoneserver.battle;

import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.StatusService;
import net.bestia.entity.component.AttackListComponent;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.zoneserver.map.MapService;

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
	private final StatusService statusService;
	private final MapService mapService;

	@Autowired
	public AttackService(
			EntityService entityService,
			StatusService statusService,
			MapService mapService,
			AttackDAO atkDao) {

		this.entityService = Objects.requireNonNull(entityService);
		this.statusService = Objects.requireNonNull(statusService);
		this.mapService = Objects.requireNonNull(mapService);
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

	public boolean knowsAttack(long entityId, int attackId) {
		final Attack attack = attackDao.findOne(attackId);
		final Entity attacker = entityService.getEntity(attackId);
		return knowsAttack(attacker, attack);
	}

	public boolean knowsAttack(Entity entity, Attack attack) {
		// TODO Fixme
		return true;
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
	public boolean canUseAttack(long attackerId, int attackId) {

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
