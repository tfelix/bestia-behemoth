package net.bestia.messages;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.domain.PlayerBestia;

/**
 * This message is send to the client to trigger a initial synchronisation about
 * all possessed bestias and the bestia master. Contains general infomration
 * about the bestias, their status and their position on the map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaInitMessage extends Message {

	private static final long serialVersionUID = 1L;

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
	public BestiaInitMessage(Message message) {
		super(message);
	}

	/**
	 * 
	 * @param message
	 */
	public BestiaInitMessage() {

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

	@Override
	public String getMessagePath() {
		return getAccountMessagePath();
	}
}
