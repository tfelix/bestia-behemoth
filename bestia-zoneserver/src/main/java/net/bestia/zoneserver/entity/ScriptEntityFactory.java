package net.bestia.zoneserver.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.entity.component.PositionComponent;
import net.bestia.zoneserver.entity.component.PositionComponentSetter;
import net.bestia.zoneserver.entity.component.ScriptComponent;

/**
 * This builds a script entity which can be used by scripts because it usually
 * has some collision detection which is used in order to perform some action
 * upon
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ScriptEntityFactory extends EntityFactory {
	
	private static final Logger LOG = LoggerFactory.getLogger(ScriptEntityFactory.class);
	
	private static final Blueprint scriptEntityBlueprint;

	static {
		Blueprint.Builder builder = new Blueprint.Builder();
		builder.addComponent(PositionComponent.class)
				.addComponent(ScriptComponent.class);

		scriptEntityBlueprint = builder.build();
	}

	@Autowired
	public ScriptEntityFactory(EntityService entityService, ZoneAkkaApi akkaApi) {
		super(entityService, akkaApi);

	}

	public Entity build(CollisionShape area) {
		
		LOG.trace("Building script entity: {} {} pos:{}.", area);

		final PositionComponentSetter posSetter = new PositionComponentSetter(area);

		final Entity entity = buildEntity(scriptEntityBlueprint, makeSet(posSetter));

		return entity;
	}
}
