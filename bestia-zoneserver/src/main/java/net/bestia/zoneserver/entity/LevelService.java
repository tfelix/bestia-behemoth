package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.zoneserver.entity.components.LevelComponent;

/**
 * This service manages entities to level up and to receive exp.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class LevelService {
	
	public static final int MAX_LEVEL = 50;

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

		final LevelComponent statusComp = entityService.getComponent(entity, LevelComponent.class)
				.orElseThrow(IllegalStateException::new);

		statusComp.setLevel(level);

		// Invalidate the status points if the entity has a status component.
		statusService.calculateStatusPoints(entity);
	}

	private void checkLevelup(Entity entity, LevelComponent levelComponent) {

		final int neededExp = (int) Math
				.round(Math.pow(levelComponent.getLevel(), 3) / 10 + 15 + levelComponent.getLevel() * 1.5);

		if (levelComponent.getExp() > neededExp) {

			levelComponent.setExp(levelComponent.getExp() - neededExp);
			levelComponent.setLevel(levelComponent.getLevel() + 1);
			checkLevelup(entity, levelComponent);

		} else {

			statusService.calculateStatusPoints(entity);
			entityService.updateComponent(levelComponent);
		}
	}

	public void addExp(Entity entity, int exp) {
		final LevelComponent levelComp = entityService.getComponent(entity, LevelComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		levelComp.setExp(levelComp.getExp() + exp);
		checkLevelup(entity, levelComp);
	}

}
