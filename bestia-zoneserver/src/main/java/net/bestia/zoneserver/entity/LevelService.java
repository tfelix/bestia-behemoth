package net.bestia.zoneserver.entity;

import java.util.Objects;

import net.entity.component.LevelComponent;
import net.bestia.zoneserver.battle.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.entity.Entity;
import net.bestia.entity.EntityService;

/**
 * This service manages entities to level up and to receive exp.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class LevelService {

	private static final Logger LOG = LoggerFactory.getLogger(LevelService.class);

	private static final int MAX_LEVEL = 10;

	private EntityService entityService;
	private StatusService statusService;

	@Autowired
	public LevelService(EntityService entityService, StatusService statusService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.statusService = Objects.requireNonNull(statusService);
	}

	/**
	 * Sets the level of the given entity to a certain level. The entity must
	 * have a {@link LevelComponent} attached to it or this will throw.
	 * 
	 * @param entity
	 *            The entity to set a level.
	 * @param level
	 *            The new level to set.
	 */
	public void setLevel(Entity entity, int level) {

		LOG.debug("Setting entity {} level to {}.", entity, level);

		Objects.requireNonNull(entity);
		
		if (level <= 0 || level > MAX_LEVEL) {
			throw new IllegalArgumentException("Level must be between 1 and " + MAX_LEVEL);
		}

		final LevelComponent lvComp = entityService.getComponent(entity, LevelComponent.class)
				.orElseThrow(IllegalStateException::new);

		lvComp.setLevel(level);

		// Invalidate the status points if the entity has a status component.
		statusService.calculateStatusPoints(entity);
	}

	/**
	 * Returns the level of the entity. The entity must possess the
	 * {@link LevelComponent} or 1 is returned.
	 */
	public int getLevel(Entity entity) {

		return entityService.getComponent(entity, LevelComponent.class)
				.map(LevelComponent::getLevel)
				.orElse(1);
	}

	private void checkLevelup(Entity entity, LevelComponent levelComponent) {

		final int neededExp = (int) Math
				.round(Math.pow(levelComponent.getLevel(), 3) / 10 + 15 + levelComponent.getLevel() * 1.5);

		LOG.trace("Entity {} has {}/{} exp for levelup.", entity, neededExp, levelComponent.getExp());

		if (levelComponent.getExp() > neededExp && levelComponent.getLevel() < MAX_LEVEL) {

			levelComponent.setExp(levelComponent.getExp() - neededExp);
			levelComponent.setLevel(levelComponent.getLevel() + 1);
			checkLevelup(entity, levelComponent);

		}

		// Finally recalculate the status if all level ups have recursively
		// resolved.
		statusService.calculateStatusPoints(entity);
		entityService.updateComponent(levelComponent);
	}

	/**
	 * Adds a amount of experience points to a entity with a level component. It
	 * will check if a level up has occurred and recalculate status points if
	 * neccesairy.
	 * 
	 * @param entity
	 * @param exp
	 */
	public void addExp(Entity entity, int exp) {
		
		LOG.trace("Entity {} gains {} exp.", entity, exp);
		
		if(exp < 0) {
			throw new IllegalArgumentException("Exp can not be negative.");
		}
		
		final LevelComponent levelComp = entityService.getComponent(entity, LevelComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		levelComp.setExp(levelComp.getExp() + exp);
		checkLevelup(entity, levelComp);
	}

	/**
	 * The current experience of the entity. Entity must possess
	 * {@link LevelComponent} or 0 will be returned.
	 * 
	 * @param entity
	 *            The entity to get its experience points.
	 * @return The current exp or 0 if the {@link LevelComponent} is missing.
	 */
	public int getExp(Entity entity) {
		return entityService.getComponent(entity, LevelComponent.class)
				.map(LevelComponent::getExp)
				.orElse(0);
	}

}
