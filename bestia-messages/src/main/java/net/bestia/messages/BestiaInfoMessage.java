package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;

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

	@JsonProperty("sp")
	private StatusPoints statusPoints;

	/**
	 * 
	 * @param message
	 *            Message to initialize the message with.
	 */
	public BestiaInfoMessage(Message message) {
		super(message);
	}

	/**
	 * Std. ctor.
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
	public BestiaInfoMessage(PlayerBestia bestia, StatusPoints status) {
		if (bestia == null) {
			throw new IllegalArgumentException("Bestia can not be null.");
		}
		if (status == null) {
			throw new IllegalArgumentException("Status can not be null.");
		}

		setAccountId(bestia.getOwner().getId());
		this.bestia = bestia;
		this.statusPoints = status;
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

	public void setBestia(PlayerBestia bestia, StatusPoints statusPoints) {
		if(bestia == null) {
			throw new IllegalArgumentException("bestia can not be null.");
		}
		if(statusPoints == null) {
			throw new IllegalArgumentException("statusPoints can not be null.");
		}
		this.bestia = bestia;
		this.statusPoints = statusPoints;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	public void setStatusPoints(StatusPoints statusPoints) {
		this.statusPoints = statusPoints;
	}
}
