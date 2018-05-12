package net.bestia.entity.factory;

import net.bestia.entity.Entity;
import net.bestia.entity.component.*;
import net.bestia.entity.component.TagComponent.Tag;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.domain.Bestia;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.battle.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * Mob factory will create entities which serve as standard mobs for the bestia
 * system.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class MobFactory {

	private static final Logger LOG = LoggerFactory.getLogger(MobFactory.class);

	private static final Blueprint mobBlueprint;

	static {
		Blueprint.Builder builder = new Blueprint.Builder();
		builder.addComponent(VisibleComponent.class)
				.addComponent(EquipComponent.class)
				.addComponent(InventoryComponent.class)
				.addComponent(PositionComponent.class)
				.addComponent(LevelComponent.class)
				.addComponent(TagComponent.class)
				.addComponent(StatusComponent.class);

		mobBlueprint = builder.build();
	}

	private EntityFactory entityFactory;
	private StatusService statusService;
	private final BestiaDAO bestiaDao;

	@Autowired
	public MobFactory(EntityFactory entityFactory, StatusService statusService, BestiaDAO bestiaDao) {

		this.entityFactory = Objects.requireNonNull(entityFactory);
		this.statusService = Objects.requireNonNull(statusService);
		this.bestiaDao = Objects.requireNonNull(bestiaDao);
	}

	public Entity build(String moDbName, long x, long y) {

		final Bestia bestia = bestiaDao.findByDatabaseName(moDbName);

		if (bestia == null) {
			LOG.warn("Database does not contain mob bestia: {}", moDbName);
			return null;
		}

		LOG.debug("Spawning mob {} ({},{}).", moDbName, x, y);

		final PositionComponentSetter posSetter = new PositionComponentSetter(new Point(x, y));
		final VisibleComponentSetter visSetter = new VisibleComponentSetter(bestia.getSpriteInfo());
		final LevelComponentSetter levelSetter = new LevelComponentSetter(bestia.getLevel(), 0);
		final TagComponentSetter tagSetter = new TagComponentSetter(Tag.PERSIST);

		final Entity mob = entityFactory.buildEntity(mobBlueprint, posSetter, visSetter, levelSetter, tagSetter);

		// Calculate the status points now.
		statusService.calculateStatusPoints(mob);

		return mob;
	}
}