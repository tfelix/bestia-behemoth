package net.bestia.messages;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BestiaMove extends Message {
	
	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.move";
	
	@JsonProperty("pX")
	private List<Integer> cordsX;
	
	@JsonProperty("pY")
	private List<Integer> cordsY;
	
	@JsonProperty("w")
	private float walkspeed;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

}
