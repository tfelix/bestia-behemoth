package net.bestia.messages.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.SpriteInfo;

public class EntityUpdateMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.update";

	public String uuid;

	@JsonProperty("s")
	private SpriteInfo spriteInfo;

	@JsonProperty("x")
	private int x;

	@JsonProperty("y")
	private int y;

	@JsonProperty("a")
	private EntityAction action;

	@JsonProperty("pbid")
	private Integer playerBestiaId;

	public EntityUpdateMessage() {
		// no op.
	}

	public EntityUpdateMessage(SpriteInfo info, EntityAction action) {
		this.spriteInfo = Objects.requireNonNull(info);
		this.action = action;
	}

	public EntityUpdateMessage(String uuid, int x, int y) {
		this.uuid = uuid;
		this.x = x;
		this.y = y;
		this.action = EntityAction.APPEAR;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public EntityAction getAction() {
		return action;
	}

	public void setAction(EntityAction action) {
		this.action = action;
	}

	public Integer getPlayerBestiaId() {
		return playerBestiaId;
	}

	public void setPlayerBestiaId(int pbid) {
		this.playerBestiaId = pbid;
	}

	@Override
	public String toString() {
		return String.format("EntityUpdateMessage[uuid: %s, x: %d, y: %d, sprite: %s, type: %s, action: %s]",
				uuid, x, y,
				spriteInfo.toString(), action.toString());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}