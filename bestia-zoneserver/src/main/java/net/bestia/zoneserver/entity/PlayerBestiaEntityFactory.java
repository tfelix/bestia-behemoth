package net.bestia.zoneserver.entity;

import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.ComponentSetter;
import net.bestia.zoneserver.entity.components.LevelComponentSetter;
import net.bestia.zoneserver.entity.components.PlayerComponentSetter;
import net.bestia.zoneserver.entity.components.PositionComponentSetter;
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

	private final EntityFactory entityFactory;
	private final Blueprint playerBestiaBlueprint;

	public PlayerBestiaEntityFactory(@Qualifier("playerBestia") Blueprint playerBestiaBlueprint,
			EntityFactory entityFactory) {

		this.playerBestiaBlueprint = Objects.requireNonNull(playerBestiaBlueprint);
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

		// Prepare the setter.
		final PositionComponentSetter posSetter = new PositionComponentSetter(playerBestia.getCurrentPosition());
		final VisibleComponentSetter visSetter = new VisibleComponentSetter(playerBestia.getOrigin().getSpriteInfo());
		final PlayerComponentSetter playerSetter = new PlayerComponentSetter(playerBestia);
		final LevelComponentSetter levelSetter = new LevelComponentSetter(playerBestia.getLevel(),
				playerBestia.getExp());

		final Set<ComponentSetter<? extends Component>> comps = EntityFactory.makeSet(posSetter, visSetter,
				playerSetter, levelSetter);

		final Entity masterEntity = entityFactory.build(playerBestiaBlueprint, comps);

		return masterEntity;
	}

}
