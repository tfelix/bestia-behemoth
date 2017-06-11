package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.hazelcast.core.MapStore;

import net.bestia.model.dao.EntityDataDAO;
import net.bestia.model.domain.EntityData;
import net.bestia.zoneserver.entity.component.TagComponent;

/**
 * Specialized class which enables the entities to be persistedly stored into a
 * persistent database. This is needed if the bestia server goes down or is shut
 * down so the world keeps persisted and can be reloaded.
 * 
 * @author Thomas Felix
 *
 */
public class EntityMapStore implements MapStore<Long, Entity> {
	
	private EntityService entityService;
	private EntityDataDAO entityDataDao;
	
	private boolean persistEntity(Entity entity) {
		final Optional<TagComponent> tag = entityService.getComponent(entity, TagComponent.class);
		
		if(!tag.isPresent()) {
			return false;
		}
		
		return tag.get().has(TagComponent.TAG_PERSIST);
	}

	@Override
	public synchronized Entity load(Long id) {
		
		final EntityData entityData = entityDataDao.findOne(id);
		
		if(entityData == null) {
			return null;
		}
		
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Map<Long, Entity> loadAll(Collection<Long> ids) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Iterable<Long> loadAllKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized void delete(Long id) {
		entityDataDao.delete(id);
	}

	@Override
	public synchronized void deleteAll(Collection<Long> ids) {
		final Iterable<EntityData> datas = entityDataDao.findAll(ids);
		entityDataDao.delete(datas);
	}

	@Override
	public synchronized void store(Long id, Entity entity) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void storeAll(Map<Long, Entity> entities) {
		// TODO Auto-generated method stub

	}

}
