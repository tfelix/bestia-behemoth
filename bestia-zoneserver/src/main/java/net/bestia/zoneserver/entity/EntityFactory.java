package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.messages.entity.EntitySpawnMessage;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.service.EntityService;

/**
 * The factory is used to create entities.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class EntityFactory {
	
	private final static Logger LOG = LoggerFactory.getLogger(EntityFactory.class);

	private final BestiaDAO bestiaDao;
	private final EntityContext entityCtx;
	private final EntityService entityService;

	@Autowired
	public EntityFactory(BestiaDAO bestiaDao, EntityContext entityCtx, EntityService entityService) {

		this.bestiaDao = Objects.requireNonNull(bestiaDao);
		this.entityCtx = Objects.requireNonNull(entityCtx);
		this.entityService = Objects.requireNonNull(entityService);

	}

	public void spawnBestia(String bestiaName, long x, long y) {

		final Bestia bestia = bestiaDao.findByDatabaseName(bestiaName);
		
		if(bestia == null) {
			LOG.warn("Bestia with name {} was not found in the database.", bestiaName);
			return;
		}

		// Save first for ID.
		final NPCEntity be = new NPCEntity(bestia.getBaseValues(), BaseValues.getNewIndividualValues(),
				BaseValues.getNullValues(), bestia.getSpriteInfo());
		be.setEntityContext(entityCtx);
		entityService.generateId(be);
		
		// Save so the position update can access the entity.
		entityService.save(be);
		
		// Position the entity.
		be.setPosition(x, y);
		entityService.save(be);

		final EntitySpawnMessage spawnMsg = new EntitySpawnMessage(be.getId());
		//entityCtx.sendMessage(spawnMsg);
	}
}
