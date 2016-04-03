package net.bestia.messages.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;

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
public class EntityMoveMessage extends AccountMessage {
	
	private class Cords implements Serializable {

		private static final long serialVersionUID = 1L;

		@JsonProperty("x")
		int x;
		
		@JsonProperty("y")
		int y;
		
		public Cords(int x, int y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public String toString() {
			return String.format("[x: %d, y: %d]", x, y);
		}
	}

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.move";

	private List<Cords> cords = new ArrayList<>();

	@JsonProperty("s")
	private int speed;

	@JsonProperty("uuid")
	private String entityId;
	
	public EntityMoveMessage() {
		// no op.
	}

	public EntityMoveMessage(String entityId, int speed) {
		super(0);
		this.entityId = entityId;
		this.speed = speed;
	}
	
	public void addCord(int x, int y) {
		this.cords.add(new Cords(x, y));
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public List<Cords> getCords() {
		return cords;
	}
	
	@JsonIgnore
	public int getPathDistance() {
		int d = 0;
		
		for(int i = 1; i < cords.size(); i++) {
			final Cords x1 = cords.get(i);
			final Cords x0 = cords.get(i-1);
			
			d += (int)Math.sqrt((x1.x - x0.x) * (x1.x - x0.x) + (x1.y - x0.y) * (x1.y - x0.y));
		}
		
		return d;
	}

	@Override
	public String toString() {
		return String.format("EntityMoveMessage[uuid: %s, accId: %d, cords: %s]", entityId, getAccountId(), cords);
	}
}
