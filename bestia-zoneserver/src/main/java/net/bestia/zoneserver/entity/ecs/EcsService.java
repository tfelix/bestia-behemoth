package net.bestia.zoneserver.entity.ecs;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.hazelcast.core.MultiMap;

@Service
public class EcsService {

	private MultiMap<Long, Component> entityComponents;

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
	
	public <T> Optional<T> getComponent(Entity e, Class<T> comp) {

		return  entityComponents.get(e.getId())
				.stream()
				.filter(c -> comp.isInstance(c))
				.map(c -> comp.cast(c))
				.findFirst();
	}
}
