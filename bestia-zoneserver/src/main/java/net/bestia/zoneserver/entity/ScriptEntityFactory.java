package net.bestia.zoneserver.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.PositionComponentSetter;
import net.bestia.zoneserver.entity.components.ScriptComponent;
import net.bestia.zoneserver.entity.components.ScriptComponentSetter;
import net.bestia.zoneserver.script.ScriptType;

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
	public ScriptEntityFactory(EntityService entityService) {
		super(entityService);

	}

	public Entity build(CollisionShape area, String scriptName, ScriptType type) {
		
		LOG.trace("Building script entity: {} {} pos:{}.", scriptName, type, area);

		final PositionComponentSetter posSetter = new PositionComponentSetter(area);
		final ScriptComponentSetter scriptSetter = new ScriptComponentSetter(scriptName, type);

		final Entity entity = build(scriptEntityBlueprint, makeSet(posSetter, scriptSetter));

		return entity;
	}
}
