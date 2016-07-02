package net.bestia.zoneserver.ecs.manager;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.Component;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.GroupManager;
import com.artemis.utils.Bag;
import com.artemis.utils.ImmutableBag;

import net.bestia.interserver.ObjectSerializer;
import net.bestia.model.dao.ZoneEntityDao;
import net.bestia.model.domain.ZoneEntity;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.entity.EcsEntityFactory;

/**
 * What will be persisted?
 * <ul>
 * <li>All visible entities (but {@link PlayerBestia}s)</li>
 * </ul>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class WorldPersistenceManager extends BaseEntitySystem {

	private final static Logger LOG = LogManager.getLogger(WorldPersistenceManager.class);

	private final String zoneName;
	private final ZoneEntityDao entitiesDAO;

	@Wire
	private GroupManager groupManager;

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

		// Persists the items.
		final ImmutableBag<Entity> entities = groupManager.getEntities(EcsEntityFactory.ECS_ITEM_GROUP);
		
		final Bag<Component> components = new Bag<>();

		for (int i = 0; i < entities.size(); i++) {
			final Entity e = entities.get(i);
			
			LOG.trace("Serialized entity id: {}", e.getId());

			e.getComponents(components);

			for (int j = 0; j < components.size(); j++) {
				final Component c = components.get(i);

				if (!(c instanceof Serializable)) {
					continue;
				}

				final byte[] data = ObjectSerializer.serializeObject((Serializable) c);
				
				final ByteBuffer buffer = ByteBuffer.allocate(data.length + Integer.BYTES);
				buffer.putInt(data.length);
				buffer.put(data);
				
				// Create and save entity.
				final ZoneEntity zoneEntity = new ZoneEntity(zoneName, buffer.array());
				entitiesDAO.save(zoneEntity);			
			}
			components.clear();
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
