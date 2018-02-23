package net.entity.component;

import java.util.Objects;

import bestia.model.domain.PlayerBestia;

public class PlayerComponentSetter extends ComponentSetter<PlayerComponent> {

	private PlayerBestia playerBestia;

	public PlayerComponentSetter(PlayerBestia playerBestia) {
		super(PlayerComponent.class);
		
		this.playerBestia = Objects.requireNonNull(playerBestia);
	}

	@Override
	protected void performSetting(PlayerComponent comp) {
		
		comp.setOwnerAccountId(playerBestia.getOwner().getId());
		comp.setPlayerBestiaId(playerBestia.getId());
	}

}
