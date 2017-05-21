package net.bestia.zoneserver.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.VisibleComponent;

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

	private static final Blueprint scriptEntityBlueprint;

	static {
		Blueprint.Builder builder = new Blueprint.Builder();
		builder.addComponent(VisibleComponent.class)
				.addComponent(PositionComponent.class)
				.addComponent(ScriptComponent.class);

		scriptEntityBlueprint = builder.build();
	}

	@Autowired
	public ScriptEntityFactory(EntityService entityService) {
		super(entityService);

	}

	public Entity build() {

		final Entity entity = build(scriptEntityBlueprint);

		return entity;
	}
}
