package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.domain.Bestia;
import net.bestia.model.shape.Point;
import net.bestia.zoneserver.script.ScriptCompiler;
import net.bestia.zoneserver.service.EntityService;

public class EntityFactory {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptCompiler.class);

	private final EntityService entityService;
	private final BestiaDAO bestiaDao;

	public EntityFactory(EntityService entityService, BestiaDAO bestiaDao) {

		this.entityService = Objects.requireNonNull(entityService);
		this.bestiaDao = Objects.requireNonNull(bestiaDao);
	}

	public void spawnMob(String mobDbName, Point pos) {

		// Get the needed data from the database about this mob.
		final Bestia b = bestiaDao.findByDatabaseName(mobDbName);

		if (b == null) {
			LOG.warn("Can not spawn mob with name: {}. Mob not found.", mobDbName);
		}
		
		final LivingEntity le = new LivingEntity(b.getBaseValues(), b.getEffortValues(), b.getSprite());
		le.setPosition(pos.getX(), pos.getY());
		LOG.debug("Spawning {} to {}", mobDbName, pos);
		entityService.put(le);
		
		// Inform all players in sight about the newly spawned entity.
	}

}
