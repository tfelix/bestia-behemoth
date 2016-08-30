package net.bestia.zoneserver.zone.entity;

import java.util.Objects;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.misc.Sprite;
import net.bestia.model.misc.SpriteType;

public class PlayerBestiaEntity extends VisibleEntity {
	
	private final PlayerBestia playerBestia;
	
	private Sprite sprite;
	
	public PlayerBestiaEntity(PlayerBestia pb) {
		
		this.playerBestia = Objects.requireNonNull(pb);
		
		this.sprite = new Sprite(pb.getOrigin().getDatabaseName(), SpriteType.PACK);
	}
	
	public long getAccountId() {
		return playerBestia.getOwner().getId();
	}
	
	public PlayerClass getPlayerClass() {
		return PlayerClass.WARRIOR;
	}
	
	@Override
	public Sprite getSprite() {
		return sprite;
	}
}
