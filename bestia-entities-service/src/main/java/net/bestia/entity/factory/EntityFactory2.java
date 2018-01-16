package net.bestia.entity.factory;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentSetter;

/**
 * The EcsEntityFactory is responsible for translating ecs blueprints into
 * working entities with all components attached and initialized.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
class EntityFactory2 {

	private final static Logger LOG = LoggerFactory.getLogger(EntityFactory2.class);

	private final EntityService entityService;

	private final Set<Class<? extends Component>> components = new HashSet<>();

	@Autowired
	EntityFactory2(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	public EntityFactory2 addComponent(Class<? extends Component> componentClass) {
		components.add(componentClass);
		return this;
	}

	public Entity build(Set<ComponentSetter<? extends Component>> setter) {

		Objects.requireNonNull(setter);

		LOG.debug("Building entity with components: {}", components);

		// Check if we can use recycled entity.
		final Entity e = entityService.newEntity();
		final Set<Component> addedComponents = new HashSet<>();
		
		final Map<Class<? extends Component>, ComponentSetter<? extends Component>> setterMap = setter.stream()
				.collect(Collectors.toMap(ComponentSetter<? extends Component>::getSupportedType, 
						Function.identity()));

		components.forEach(compClazz -> {
			final Component comp = entityService.newComponent(compClazz);

			if(setterMap.containsKey(compClazz)) {
				setterMap.get(compClazz).setComponent(comp);
			}
			
			addedComponents.add(comp);
		});

		// Attaches the component to the entity in a batch for performance reasons.
		entityService.attachComponents(e, addedComponents);
		return e;
	}
}
