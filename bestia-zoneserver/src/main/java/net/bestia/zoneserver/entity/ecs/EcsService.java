package net.bestia.zoneserver.entity.ecs;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;

@Service
public class EcsService {

	private final static String ECS_ENTITY_MAP = "entities.ecs";
	private final static String ENTITIES_MAP = "entities";

	private final IMap<Long, Entity> entities;
	private final MultiMap<Long, Component> entityComponents;

	public EcsService(HazelcastInstance hz) {
		Objects.requireNonNull(hz);

		entityComponents = hz.getMultiMap(ECS_ENTITY_MAP);
		entities = hz.getMap(ENTITIES_MAP);
	}

	public <T> Optional<T> getComponent(long entityId, Class<T> clazz) {

		return entityComponents.get(entityId)
				.stream()
				.filter(c -> clazz.isInstance(c))
				.map(c -> clazz.cast(c))
				.findFirst();
	}
	
	public void addComponent(long entityId, Component comp) {
		throw new IllegalStateException();
	}
	
	public void removeComponent(long entityId, Component comp) {
		throw new IllegalStateException();
	}
	
	public Collection<Entity> getEntitiesWithComponent(Class<Component>... compClass) {
		throw new IllegalStateException();
	}

	/**
	 * 
	 * @param e
	 * @param comp
	 * @return
	 */
	public boolean hasComponent(Entity e, Class<? extends Component> comp) {

		return entityComponents.get(e.getId())
				.stream()
				.filter(c -> comp.isInstance(c))
				.findFirst()
				.map(x -> true)
				.orElse(false);
	}
}
