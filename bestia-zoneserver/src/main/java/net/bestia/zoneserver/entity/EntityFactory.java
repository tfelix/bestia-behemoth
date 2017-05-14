package net.bestia.zoneserver.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.ComponentSetter;

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

	EntityFactory(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Returns the given components as a set and is a neat helper method to
	 * prepare calling the {@link #build(Blueprint, Set)} method.
	 * 
	 * @param components
	 *            Components to be transformed into a set.
	 * @return The set containing all the given components.
	 */
	@SafeVarargs
	static Set<ComponentSetter<? extends Component>> makeSet(ComponentSetter<? extends Component>... components) {
		return new HashSet<>(Arrays.asList(components));
	}

	Entity build(Blueprint blueprint) {
		return build(blueprint, Collections.emptySet());
	}

	Entity build(Blueprint blueprint,
			Set<ComponentSetter<? extends Component>> setter) {

		Objects.requireNonNull(blueprint);
		Objects.requireNonNull(setter);

		LOG.trace("Creating entity with: {}", blueprint);

		final Entity e = entityService.newEntity();

		// Add all given components in the blueprint.
		for (Class<? extends Component> compClazz : blueprint.getComponents()) {

			final Component addedComp = entityService.addComponent(e, compClazz);

			// Fill the component with values from a supported setter.
			setter.stream()
					.filter(s -> s.getSupportedType().equals(compClazz))
					.findAny()
					.ifPresent(s -> {
						s.setComponent(addedComp);
					});

			// Save the component.
			entityService.updateComponent(addedComp);
		}

		// Use the setter to fill the components with data.

		return e;
	}

}
