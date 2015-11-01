package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.domain.PlayerBestia;

/**
 * This message is send to the client to trigger a initial synchronization about
 * all possessed bestias and the bestia master. Contains general information
 * about the bestias, their status and their position on the map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaInfoMessage extends Message {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "bestia.info";

	@JsonProperty("im")
	private boolean isMaster;

	@JsonProperty("b")
	private PlayerBestia bestia;

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

	/**
	 * 
	 * @param numberOfExtraSlots
	 *            Number of extra
	 * @param masterBestia
	 * @param bestias
	 */
	public BestiaInfoMessage(Message msg, PlayerBestia bestia, boolean isMaster) {
		super(msg);
		if (msg == null) {
			throw new IllegalArgumentException("Message can not be null.");
		}
		if (bestia == null) {
			throw new IllegalArgumentException("Bestia can not be null.");
		}

		this.isMaster = isMaster;
		this.bestia = bestia;
	}

	/**
	 * 
	 * @param bestia
	 */
	public BestiaInfoMessage(PlayerBestia bestia) {
		if (bestia == null) {
			throw new IllegalArgumentException("Bestia can not be null.");
		}
		
		setAccountId(bestia.getOwner().getId());
		this.bestia = bestia;
	}

	@JsonIgnore
	public boolean isMaster() {
		return isMaster;
	}

	public void setMaster(boolean isMaster) {
		this.isMaster = isMaster;
	}

	public PlayerBestia getBestia() {
		return bestia;
	}

	public void setBestia(PlayerBestia bestia) {
		this.bestia = bestia;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}
}
