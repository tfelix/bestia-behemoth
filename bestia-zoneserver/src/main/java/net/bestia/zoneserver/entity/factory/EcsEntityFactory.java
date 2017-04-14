package net.bestia.zoneserver.entity.factory;

import java.util.Objects;

import org.springframework.stereotype.Component;

import net.bestia.zoneserver.entity.ecs.EcsEntityService;

/**
 * The EcsEntityFactory is responsible for translating ecs blueprints into
 * working entities with all components attached and initialized.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class EcsEntityFactory {
	
	private final EcsEntityService entityService;
	
	public EcsEntityFactory(EcsEntityService entityService) {
		
		this.entityService = Objects.requireNonNull(entityService);
	}
	
	

}
