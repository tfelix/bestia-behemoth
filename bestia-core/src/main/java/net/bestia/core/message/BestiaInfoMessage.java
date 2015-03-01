package net.bestia.core.message;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.core.game.model.PlayerBestia;

public class BestiaInfoMessage extends Message {
	
	public final static String MESSAGE_ID = "bestia.info";
	
	@JsonProperty("ns")
	private int numberOfSlots;
	@JsonProperty("b")
	private List<PlayerBestia> bestias = new ArrayList<PlayerBestia>();
	@JsonProperty("m")
	private int masterId;
	@JsonProperty("s")
	private int selectedId;


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
		return numberOfSlots;
	}

	public void setNumberOfSlots(int numberOfSlots) {
		this.numberOfSlots = numberOfSlots;
	}

	public List<PlayerBestia> getBestias() {
		return bestias;
	}

	public void setBestias(List<PlayerBestia> bestias) {
		this.bestias = bestias;
	}

	public int getMasterId() {
		return masterId;
	}

	public void setMasterId(int masterId) {
		this.masterId = masterId;
	}

	public int getSelectedId() {
		return selectedId;
	}

	public void setSelectedId(int selectedId) {
		this.selectedId = selectedId;
	}

}
