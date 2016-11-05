package net.bestia.zoneserver.entity;

import java.util.Objects;

import net.bestia.model.domain.PlayerBestia;

public class MasterBestiaEntity extends PlayerBestiaEntity {
	
	private final PlayerBestia playerBestia;
	
	public MasterBestiaEntity(PlayerBestia pb) {
		super(pb);
		
		this.playerBestia = Objects.requireNonNull(pb);
	}
	
	public PlayerClass getPlayerClass() {
		return PlayerClass.WARRIOR;
	}
}
