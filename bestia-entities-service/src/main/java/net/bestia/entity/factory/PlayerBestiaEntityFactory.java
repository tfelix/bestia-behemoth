package net.bestia.entity.factory;

import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.entity.Entity;
import net.bestia.entity.StatusService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentSetter;
import net.bestia.entity.component.EquipComponent;
import net.bestia.entity.component.InventoryComponent;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.LevelComponentSetter;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.PlayerComponentSetter;
import net.bestia.entity.component.PlayerStatusComponentSetter;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.PositionComponentSetter;
import net.bestia.entity.component.StatusComponent;
import net.bestia.entity.component.TagComponent;
import net.bestia.entity.component.TagComponent.Tag;
import net.bestia.entity.component.TagComponentSetter;
import net.bestia.entity.component.VisibleComponent;
import net.bestia.entity.component.VisibleComponentSetter;

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

	private static final Blueprint playerBestiaBlueprint;

	static {
		Blueprint.Builder builder = new Blueprint.Builder();
		builder.addComponent(VisibleComponent.class)
				.addComponent(EquipComponent.class)
				.addComponent(InventoryComponent.class)
				.addComponent(PositionComponent.class)
				.addComponent(PlayerComponent.class)
				.addComponent(TagComponent.class)
				.addComponent(LevelComponent.class)
				.addComponent(StatusComponent.class);

		playerBestiaBlueprint = builder.build();
	}

	private final StatusService statusService;
	private final EntityFactory entityFactory;

	@Autowired
	public PlayerBestiaEntityFactory(EntityFactory entityFactory, StatusService statusService) {
		
		this.entityFactory = Objects.requireNonNull(entityFactory);
		this.statusService = Objects.requireNonNull(statusService);
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
		final TagComponentSetter tagSetter = new TagComponentSetter(Tag.PLAYER);
		final LevelComponentSetter levelSetter = new LevelComponentSetter(playerBestia.getLevel(),
				playerBestia.getExp());
		final PlayerStatusComponentSetter statusSetter = new PlayerStatusComponentSetter(playerBestia);

		final Set<ComponentSetter<? extends Component>> comps = EntityFactory.makeSet(
				posSetter,
				visSetter,
				playerSetter,
				levelSetter,
				tagSetter,
				statusSetter);

		final Entity playerEntity = entityFactory.buildEntity(playerBestiaBlueprint, comps);
		
		// Calculate the status points now.
		statusService.calculateStatusPoints(playerEntity);

		return playerEntity;
	}

}
