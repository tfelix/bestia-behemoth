package net.bestia.zoneserver.ecs.entity;

import net.bestia.model.domain.PlayerBestia;

public class PlayerEntityBuilder extends EntityBuilder {
	
	PlayerBestia playerBestia;
	
	public PlayerEntityBuilder(PlayerBestia playerBestia) {
		this.playerBestia = playerBestia;
	}

}
