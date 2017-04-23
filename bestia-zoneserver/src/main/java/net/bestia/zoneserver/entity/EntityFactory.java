package net.bestia.zoneserver.entity;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.entity.components.ComponentSetter;

/**
 * The EcsEntityFactory is responsible for translating ecs blueprints into
 * working entities with all components attached and initialized.
 * 
 * @author Thomas Felix
 *
 */
@Component
class EntityFactory {

	private final static Logger LOG = LoggerFactory.getLogger(EntityFactory.class);

	private final EntityService entityService;

	EntityFactory(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}
	
	Entity build(Blueprint blueprint) {
		return build(blueprint, Collections.emptySet());
	}

	Entity build(Blueprint blueprint,
			Set<ComponentSetter<? extends net.bestia.zoneserver.entity.components.Component>> setter) {

		LOG.trace("Creating entity with: {}", blueprint);

		final Entity e = entityService.newEntity();

		// Add all given components in the blueprint.
		for (Class<? extends net.bestia.zoneserver.entity.components.Component> compClazz : blueprint.getComponents()) {

			final net.bestia.zoneserver.entity.components.Component addedComp = entityService.addComponent(e,
					compClazz);

			setter.stream().filter(s -> s.getSupportedType().equals(compClazz)).findAny().ifPresent(s -> {
				s.setComponent(addedComp);
			});

			// Save the component.
			entityService.update(addedComp);
		}

		// Use the setter to fill the components with data.

		return e;
	}

}
