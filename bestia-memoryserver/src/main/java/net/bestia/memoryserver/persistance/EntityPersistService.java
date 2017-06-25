package net.bestia.memoryserver.persistance;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.TagComponent;
import net.bestia.model.dao.EntityDataDAO;
import net.bestia.model.domain.EntityData;
import net.bestia.util.ObjectSerializer;

@Service
public class EntityPersistService {

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

		entityDao.delete(id);
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
		
		// Only store if it was flagged with a tag.
		Optional<TagComponent> tagComp = entityService.getComponent(entity, TagComponent.class);
		
		if(!tagComp.isPresent() || !tagComp.get().has(TagComponent.TAG_PERSIST)) {
			return;
		}
		
		// Find all the components of the entity.
		final byte[] data = serializer.serialize(entity);
		final EntityData entityData = new EntityData();
		entityData.setId(entity.getId());
		entityData.setData(data);
		entityDao.save(entityData);
		
	}

}
