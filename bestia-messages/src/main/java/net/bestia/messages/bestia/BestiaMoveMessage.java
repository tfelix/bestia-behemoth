package net.bestia.messages.bestia;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.InputMessage;

public class BestiaMoveMessage extends InputMessage {

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


	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

	@Override
	public String toString() {
		return String.format(
				"BestiaMoveMessage[pathX: %s, pathY: %s, walkspeed: %f]",
				cordsX.toString(), cordsY.toString(), walkspeed);
	}
}