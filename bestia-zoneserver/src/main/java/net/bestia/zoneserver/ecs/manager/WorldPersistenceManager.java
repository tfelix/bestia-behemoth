package net.bestia.zoneserver.ecs.manager;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.Component;
import com.artemis.Entity;
import com.artemis.Manager;
import com.artemis.annotations.Wire;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.bestia.model.dao.ZoneEntityDao;

@Wire
public class WorldPersistenceManager extends BaseEntitySystem {

	private final static Logger LOG = LogManager.getLogger(WorldPersistenceManager.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<Integer, Entity> trackedEntities = new HashMap<>();

	private final String zoneName;
	private final ZoneEntityDao entitiesDAO;

	/**
	 * 
	 * @param saveFolder
	 * @param zoneName
	 *            Name of the zone server. This will be the name of the
	 *            subfolder which will be created for the entities to be
	 *            persisted.
	 */
	public WorldPersistenceManager(String zoneName, ZoneEntityDao entitiesDao) {
		super(Aspect.all());

		if (zoneName == null || zoneName.isEmpty()) {
			throw new IllegalArgumentException("ZoneName can not be null or empty.");
		}

		if (entitiesDao == null) {
			throw new IllegalArgumentException("EntitiesDao can not be null.");
		}

		this.entitiesDAO = entitiesDao;
		this.zoneName = zoneName;
		
		setEnabled(false);
	}

	public void save() throws IOException {
		
		final StringWriter writer = new StringWriter();
		
		final IntBag entities = getEntityIds();

		//final WorldSerializationManager manager = world.getSystem(WorldSerializationManager.class);
		//final SaveFileFormat save = new SaveFileFormat(entities);
		
		//manager.save(writer, save);
		//String json = writer.toString();

		for (int i = 0; i < entities.size(); i++) {
			final Bag<Component> components = new Bag<>();
			final int entityId = entities.get(i);
			
			final Entity e = world.getEntity(entityId);
			e.getComponents(components);
			
			// TODO Check if this entity should be persisted.
			// Dont persist player or script entities which can be regenerated
			// from database or during startup.

			try {
				final String json2 = mapper.writeValueAsString(components);
				LOG.trace("Serialized entity: {}", json2);
			} catch (JsonGenerationException | JsonMappingException e1) {
				LOG.warn("Can not serialize.", e1);
			}
		}
	}

	public void load() {

		// TODO Schreiben.
	}

	@Override
	protected void processSystem() {
		// no op.
	}

	
}
