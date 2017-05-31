package net.bestia.zoneserver.entity;

import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.entity.component.Component;
import net.bestia.zoneserver.entity.component.ComponentSetter;
import net.bestia.zoneserver.entity.component.EquipComponent;
import net.bestia.zoneserver.entity.component.InventoryComponent;
import net.bestia.zoneserver.entity.component.LevelComponent;
import net.bestia.zoneserver.entity.component.LevelComponentSetter;
import net.bestia.zoneserver.entity.component.PlayerComponent;
import net.bestia.zoneserver.entity.component.PlayerComponentSetter;
import net.bestia.zoneserver.entity.component.PlayerStatusComponentSetter;
import net.bestia.zoneserver.entity.component.PositionComponent;
import net.bestia.zoneserver.entity.component.PositionComponentSetter;
import net.bestia.zoneserver.entity.component.StatusComponent;
import net.bestia.zoneserver.entity.component.VisibleComponent;
import net.bestia.zoneserver.entity.component.VisibleComponentSetter;

/**
 * The factory is used to create player entities which can be controlled via a
 * player.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class PlayerBestiaEntityFactory extends EntityFactory {

	private static final Logger LOG = LoggerFactory.getLogger(PlayerBestiaEntityFactory.class);

	private static final Blueprint playerBestiaBlueprint;

	static {
		Blueprint.Builder builder = new Blueprint.Builder();
		builder.addComponent(VisibleComponent.class)
				.addComponent(EquipComponent.class)
				.addComponent(InventoryComponent.class)
				.addComponent(PositionComponent.class)
				.addComponent(PlayerComponent.class)
				.addComponent(LevelComponent.class)
				.addComponent(StatusComponent.class);

		playerBestiaBlueprint = builder.build();
	}

	@Autowired
	public PlayerBestiaEntityFactory(EntityService entityService) {
		super(entityService);
		// no op.
	}

	/**
	 * Builds and fills up the entity with values for the player bestia.
	 * 
	 * @param playerBestia
	 *            The player bestia.
	 * @return A newly created entity for this player bestia.
	 */
	public Entity build(PlayerBestia playerBestia) {
		Objects.requireNonNull(playerBestia);

		LOG.trace("Building player bestia entity: {}.", playerBestia);

		// Prepare the setter.
		final PositionComponentSetter posSetter = new PositionComponentSetter(playerBestia.getCurrentPosition());
		final VisibleComponentSetter visSetter = new VisibleComponentSetter(playerBestia.getOrigin().getSpriteInfo());
		final PlayerComponentSetter playerSetter = new PlayerComponentSetter(playerBestia);
		final LevelComponentSetter levelSetter = new LevelComponentSetter(playerBestia.getLevel(),
				playerBestia.getExp());
		final PlayerStatusComponentSetter statusSetter = new PlayerStatusComponentSetter(playerBestia);

		final Set<ComponentSetter<? extends Component>> comps = EntityFactory.makeSet(posSetter, visSetter,
				playerSetter, levelSetter, statusSetter);

		final Entity masterEntity = buildEntity(playerBestiaBlueprint, comps);

		return masterEntity;
	}

}
