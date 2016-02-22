package net.bestia.zoneserver.ecs.manager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.Manager;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.bestia.model.dao.MapEntityDAO;

public class WorldPersistenceManager extends Manager {

	private final static Logger LOG = LogManager.getLogger(WorldPersistenceManager.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<Integer, Entity> trackedEntities = new HashMap<>();

	private final String zoneName;
	private final MapEntityDAO entitiesDAO;

	/**
	 * 
	 * @param saveFolder
	 * @param zoneName
	 *            Name of the zone server. This will be the name of the
	 *            subfolder which will be created for the entities to be
	 *            persisted.
	 */
	public WorldPersistenceManager(String zoneName, MapEntityDAO entitiesDao) {

		if (zoneName == null || zoneName.isEmpty()) {
			throw new IllegalArgumentException("ZoneName can not be null or empty.");
		}

		if (entitiesDao == null) {
			throw new IllegalArgumentException("EntitiesDao can not be null.");
		}

		this.entitiesDAO = entitiesDao;
		this.zoneName = zoneName;
	}

	public void save() throws IOException {

		for (Entity e : trackedEntities.values()) {
			// TODO Check if this entity should be persisted.
			// Dont persist player or script entities which can be regenerated
			// from database or during startup.

			try {
				final String entityStr = mapper.writeValueAsString(e);
			} catch (JsonGenerationException | JsonMappingException e1) {

			}
		}
	}

	public void load() {

		// TODO Schreiben.
	}

	@Override
	public void added(Entity e) {
		trackedEntities.put(e.getId(), e);
	}

	@Override
	public void deleted(Entity e) {
		trackedEntities.remove(e.getId());
	}
}
