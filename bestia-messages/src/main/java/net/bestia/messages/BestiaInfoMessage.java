package net.bestia.messages;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.core.game.model.PlayerBestia;

public class BestiaInfoMessage extends Message {
	
	public final static String MESSAGE_ID = "bestia.info";
	
	@JsonProperty("ns")
	private int numberOfExtraSlots;
	@JsonProperty("b")
	private List<PlayerBestia> bestias = new ArrayList<PlayerBestia>();
	@JsonProperty("bm")
	private PlayerBestia master;


	/**
	 * 
	 * @param message
	 */
	public BestiaInfoMessage(Message message) {
		super(message);
	}
	
	/**
	 * 
	 * @param message
	 */
	public BestiaInfoMessage() {
		
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public int getNumberOfSlots() {
		return numberOfExtraSlots;
	}

	public void setNumberOfSlots(int numberOfSlots) {
		this.numberOfExtraSlots = numberOfSlots;
	}

	public List<PlayerBestia> getBestias() {
		return bestias;
	}

	public void setBestias(List<PlayerBestia> bestias) {
		this.bestias = bestias;
	}

	public PlayerBestia getMaster() {
		return master;
	}

	public void setMaster(PlayerBestia master) {
		this.master = master;
	}
}
