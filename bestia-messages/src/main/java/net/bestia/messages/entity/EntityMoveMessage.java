package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.geometry.Point;

/**
 * This message is send from the server to all clients as soon as a movement is
 * started. It contains the path of the bestia as well as the speed. As long as
 * nothing changes along the path no further update is send and the client can
 * use this information to interpolate the movement of the entity. If a new
 * {@link EntityMoveMessage} is send by the server it takes precedence over the
 * old one.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.>
 *
 */
public class EntityMoveMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.move";

	private List<Point> cords = new ArrayList<>();

	@JsonProperty("s")
	private final int speed;

	@JsonProperty("uuid")
	private final long entityId;

	public EntityMoveMessage() {
		this(0, 0);
	}

	public EntityMoveMessage(long entityId, int speed) {
		super(0);
		this.entityId = entityId;
		this.speed = speed;
	}

	public void addCord(int x, int y) {
		this.cords.add(new Point(x, y));
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public long getEntityId() {
		return entityId;
	}

	public List<Point> getPath() {
		return cords;
	}
	
	@Override
	public String toString() {
		return String.format("EntityMoveMessage[uuid: %s, accId: %d, cords: %s]", entityId, getAccountId(), cords);
	}
}
