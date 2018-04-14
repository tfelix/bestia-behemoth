package net.bestia.entity.factory;

import net.bestia.entity.Entity;
import net.bestia.entity.component.*;
import net.bestia.entity.component.TagComponent.Tag;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.battle.StatusService;
import net.bestia.zoneserver.entity.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.Set;

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

	private static final Blueprint PLAYER_BESTIA_BLUEPRINT;

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

		PLAYER_BESTIA_BLUEPRINT = builder.build();
	}

	private final StatusService statusService;
	private final EntityFactory entityFactory;
	private final InventoryService inventoryService;

	@Autowired
	public PlayerBestiaEntityFactory(EntityFactory entityFactory,
	                                 StatusService statusService,
	                                 InventoryService inventoryService) {
		
		this.entityFactory = Objects.requireNonNull(entityFactory);
		this.statusService = Objects.requireNonNull(statusService);
		this.inventoryService = Objects.requireNonNull(inventoryService);
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

		final Entity playerEntity = entityFactory.buildEntity(PLAYER_BESTIA_BLUEPRINT, comps);
		
		// Calculate the status points now.
		// FIXME das hier in die entsprechenden setter einbauen.
		statusService.calculateStatusPoints(playerEntity);
		inventoryService.updateMaxWeight(playerEntity);

		return playerEntity;
	}

}
