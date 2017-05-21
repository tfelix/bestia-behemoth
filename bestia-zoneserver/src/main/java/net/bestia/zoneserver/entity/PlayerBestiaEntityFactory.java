package net.bestia.zoneserver.entity;

import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.ComponentSetter;
import net.bestia.zoneserver.entity.components.EquipComponent;
import net.bestia.zoneserver.entity.components.InventoryComponent;
import net.bestia.zoneserver.entity.components.LevelComponent;
import net.bestia.zoneserver.entity.components.LevelComponentSetter;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.entity.components.PlayerComponentSetter;
import net.bestia.zoneserver.entity.components.PlayerStatusComponentSetter;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.PositionComponentSetter;
import net.bestia.zoneserver.entity.components.StatusComponent;
import net.bestia.zoneserver.entity.components.VisibleComponent;
import net.bestia.zoneserver.entity.components.VisibleComponentSetter;

/**
 * The factory is used to create player entities which can be controlled via a
 * player.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class PlayerBestiaEntityFactory {

	private static final Logger LOG = LoggerFactory.getLogger(PlayerBestiaEntityFactory.class);

	private final EntityFactory entityFactory;
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

	public PlayerBestiaEntityFactory(EntityFactory entityFactory) {

		this.entityFactory = Objects.requireNonNull(entityFactory);
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

		final Entity masterEntity = entityFactory.build(playerBestiaBlueprint, comps);

		return masterEntity;
	}

}
