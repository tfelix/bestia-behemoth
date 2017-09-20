package net.bestia.memoryserver.persistance;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.TagComponent;
import net.bestia.model.dao.EntityDataDAO;
import net.bestia.model.domain.EntityData;
import net.bestia.util.ObjectSerializer;

@Service
public class EntityPersistService {
	
	private final Logger LOG = LoggerFactory.getLogger(EntityPersistService.class);

	private final ObjectSerializer<Entity> serializer = new ObjectSerializer<>();
	private final EntityDataDAO entityDao;
	private final EntityService entityService;

	@Autowired
	public EntityPersistService(EntityDataDAO entityDao, EntityService entityService) {

		this.entityDao = Objects.requireNonNull(entityDao);
		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Deletes the entity and all attached components from the permanent
	 * storage.
	 * 
	 * @param id
	 *            Deletes the entity and all attached component from the
	 *            storage.
	 */
	public void delete(Long id) {
		try {
			entityDao.delete(id);
		} catch (EmptyResultDataAccessException e) {
			// Entity did not exist. Not important. Ignore.
		}
	}

	/**
	 * Loads the entity with the given id from the permanent storage.
	 * 
	 * @param id
	 * @return
	 */
	public Entity load(Long id) {

		final EntityData data = entityDao.findOne(id);

		if (data == null) {
			LOG.debug("Did not find entity {} inside database. Returning null.", id);
			return null;
		}

		final Entity entity = serializer.deserialize(data.getData());
		return entity;
	}

	/**
	 * Stores the entity into the permanent storage.
	 * 
	 * @param entity
	 *            The entity to be permanently stored.
	 */
	public void store(Entity entity) {
		Objects.requireNonNull(entity);

		// Only store if it was flagged with a tag.
		final Optional<TagComponent> tagComp = entityService.getComponent(entity, TagComponent.class);

		if (!tagComp.isPresent() || !tagComp.get().has(TagComponent.Tag.PERSIST)) {
			return;
		}
		
		LOG.debug("Entity {} is tagged with PERSIST. Storing it.", entity);

		// Find all the components of the entity.
		final byte[] data = serializer.serialize(entity);
		final EntityData entityData = new EntityData();
		entityData.setId(entity.getId());
		entityData.setData(data);
		entityDao.save(entityData);

	}

}
