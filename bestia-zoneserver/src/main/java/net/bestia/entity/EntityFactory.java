package net.bestia.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.zoneserver.actor.entity.EntityWorkerActor;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentSetter;
import net.bestia.entity.component.deleter.EntityCache;

/**
 * The EcsEntityFactory is responsible for translating ecs blueprints into
 * working entities with all components attached and initialized.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
class EntityFactory {

	private final static Logger LOG = LoggerFactory.getLogger(EntityFactory.class);

	private final EntityService entityService;
	private final EntityCache cache;
	private final ZoneAkkaApi akkaApi;

	@Autowired
	EntityFactory(
			EntityService entityService,
			ZoneAkkaApi akkaApi,
			EntityCache recycler) {

		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.cache = Objects.requireNonNull(recycler);
	}

	/**
	 * Returns the given components as a set and is a neat helper method to
	 * prepare calling the {@link #buildEntity(Blueprint, Set)} method.
	 * 
	 * @param components
	 *            Components to be transformed into a set.
	 * @return The set containing all the given components.
	 */
	@SafeVarargs
	static Set<ComponentSetter<? extends Component>> makeSet(ComponentSetter<? extends Component>... components) {
		return new HashSet<>(Arrays.asList(components));
	}

	public Entity buildEntity(Blueprint blueprint) {
		return buildEntity(blueprint, Collections.emptySet());
	}

	public Entity buildEntity(Blueprint blueprint,
			Set<ComponentSetter<? extends Component>> setter) {

		Objects.requireNonNull(blueprint);
		Objects.requireNonNull(setter);

		LOG.debug("Creating entity with: {}", blueprint);

		// Check if we can use recycled entity.
		Entity e = cache.getEntity();

		if (e == null) {
			LOG.debug("No recycled entity present. Creating new entity.");
			e = entityService.newEntity();
		}

		// Start a actor for this entity.
		akkaApi.sendToActor(EntityWorkerActor.NAME, e.getId());
		
		List<Component> addedComponents = new ArrayList<>(blueprint.getComponents().size());

		// Add all given components in the blueprint.
		for (Class<? extends Component> compClazz : blueprint.getComponents()) {

			Component addedComp = cache.getComponent(compClazz);
			
			if(addedComp == null) {
				addedComp = entityService.newComponent(compClazz);
			}

			// Fill the component with values from a supported setter.
			final Optional<ComponentSetter<? extends Component>> foundSetter = setter.stream()
					.filter(s -> s.getSupportedType().equals(compClazz))
					.findAny();
			
			if(foundSetter.isPresent()) {
				foundSetter.get().setComponent(addedComp);
			}
			
			addedComponents.add(addedComp);
		}
		
		// Attaches the existing component to the entity.
		entityService.attachComponents(e, addedComponents);

		return e;
	}
}
