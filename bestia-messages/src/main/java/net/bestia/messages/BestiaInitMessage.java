package net.bestia.messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

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
	private int numberOfSlots;
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
	
	/**
	 * 
	 * @param numberOfExtraSlots Number of extra 
	 * @param masterBestia
	 * @param bestias
	 */
	public BestiaInitMessage(Message msg, int numberOfSlots, PlayerBestia masterBestia, Collection<PlayerBestia> bestias) {
		if(msg == null) {
			throw new IllegalArgumentException("Message can not be null.");
		}
		if(numberOfSlots <= 0) {
			throw new IllegalArgumentException("NumberOfSlots can not be 0 or negative.");
		}
		if(masterBestia == null) {
			throw new IllegalArgumentException("MasterBestia can not be null.");
		}
		if(bestias == null) {
			throw new IllegalArgumentException("Bestias can not be null.");
		}
		
		setAccountId(msg.getAccountId());
		
		this.numberOfSlots = numberOfSlots;
		this.bestias = Lists.newArrayList(bestias);
		this.master = masterBestia;
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

	public PlayerBestia getMaster() {
		return master;
	}

	public void setMaster(PlayerBestia master) {
		this.master = master;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}
}
