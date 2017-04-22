package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The EcsEntityFactory is responsible for translating ecs blueprints into
 * working entities with all components attached and initialized.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class EntityFactory {

	private final static Logger LOG = LoggerFactory.getLogger(EntityFactory.class);
	
	private final EntityService entityService;

	public EntityFactory(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}
	
	public Entity build(Blueprint blueprint) {
		
		LOG.trace("Creating entity with: {}", blueprint);
		
		final Entity e = entityService.newEntity();
		
		// Add all given components in the blueprint.
		for(Class<? extends net.bestia.zoneserver.entity.components.Component> compClazz : blueprint.getComponents()) {
			
			entityService.addComponent(e, compClazz);
		}
		
		return e;
	}

}
