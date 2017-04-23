package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.entity.components.PositionComponentSetter;

@Component
public class PlayerBestiaEntityFactory {

	private EntityFactory entityFactory;
	private Blueprint playerBestiaBlueprint;

	public PlayerBestiaEntityFactory(@Qualifier("playerBestia") Blueprint playerBestiaBlueprint) {

		this.playerBestiaBlueprint = Objects.requireNonNull(playerBestiaBlueprint);
	}

	public Entity build(PlayerBestia playerBestia) {

		final PositionComponentSetter posSetter = new PositionComponentSetter(playerBestia.getCurrentPosition());

		final Entity masterEntity = entityFactory.build(playerBestiaBlueprint);

		return masterEntity;
	}

}
