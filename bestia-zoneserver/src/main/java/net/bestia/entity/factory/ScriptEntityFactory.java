package net.entity.factory;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.entity.Entity;
import net.entity.component.PositionComponent;
import net.entity.component.PositionComponentSetter;
import net.entity.component.ScriptComponent;
import bestia.model.geometry.CollisionShape;

/**
 * This builds a script entity which can be used by scripts because it usually
 * has some collision detection which is used in order to perform some action
 * upon
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ScriptEntityFactory {
	
	private static final Logger LOG = LoggerFactory.getLogger(ScriptEntityFactory.class);
	
	private static final Blueprint scriptEntityBlueprint;
	private final EntityFactory entityFactory;

	static {
		Blueprint.Builder builder = new Blueprint.Builder();
		builder.addComponent(PositionComponent.class)
				.addComponent(ScriptComponent.class);

		scriptEntityBlueprint = builder.build();
	}

	@Autowired
	public ScriptEntityFactory(EntityFactory entityFactory) {
		
		this.entityFactory = Objects.requireNonNull(entityFactory);
	}

	public Entity build(CollisionShape area) {
		
		LOG.trace("Building script entity: {} {} pos:{}.", area);

		final PositionComponentSetter posSetter = new PositionComponentSetter(area);		
		final Entity entity = entityFactory.buildEntity(scriptEntityBlueprint, EntityFactory.makeSet(posSetter));

		return entity;
	}
}
