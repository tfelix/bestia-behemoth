package net.bestia.zoneserver.entity;

import java.util.Objects;

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

	private final EntityService entityService;

	public EntityFactory(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

}
