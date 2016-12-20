package net.bestia.messages.bestia;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.messages.entity.EntityMoveMessage;

public class BestiaMoveMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.move";

	@JsonProperty("pX")
	private List<Integer> cordsX;

	@JsonProperty("pY")
	private List<Integer> cordsY;

	@JsonProperty("w")
	private float walkspeed;

	public List<Integer> getCordsX() {
		return cordsX;
	}

	public void setCordsX(List<Integer> cordsX) {
		this.cordsX = cordsX;
	}

	public List<Integer> getCordsY() {
		return cordsY;
	}

	public void setCordsY(List<Integer> cordsY) {
		this.cordsY = cordsY;
	}

	public float getWalkspeed() {
		return walkspeed;
	}

	public void setWalkspeed(float walkspeed) {
		this.walkspeed = walkspeed;
	}

	/**
	 * Converts this message to a entity move message which has a slightly
	 * different format. FORMAT ANPASSEN.
	 * 
	 * @return
	 */
	public EntityMoveMessage getEntityMoveMessage() {
		return null;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format(
				"BestiaMoveMessage[pathX: %s, pathY: %s, walkspeed: %f]",
				cordsX.toString(), cordsY.toString(), walkspeed);
	}
}
