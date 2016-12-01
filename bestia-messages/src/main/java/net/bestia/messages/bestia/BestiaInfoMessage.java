package net.bestia.messages.bestia;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;
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
public class BestiaInfoMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "bestia.info";

	@JsonProperty("im")
	private boolean isMaster;

	@JsonProperty("b")
	private PlayerBestia bestia;

	@JsonProperty("eeid")
	private long entityId;

	@JsonProperty("sp")
	private StatusPoints statusPoints;

	public BestiaInfoMessage() {
		// no op.
	}

	/**
	 * 
	 * @param message
	 *            Message to initialize the message with.
	 */
	public BestiaInfoMessage(AccountMessage message) {
		super(message);
	}

	/**
	 * Std. ctor.
	 * 
	 * @param pb
	 * @param rbimsg
	 */
	public BestiaInfoMessage(AccountMessage message, long entityId, PlayerBestia pb, StatusPoints sp) {
		super(message);

		Objects.requireNonNull(pb);

		this.entityId = entityId;
		isMaster = pb.getMaster() != null;
		bestia = pb;
		statusPoints = Objects.requireNonNull(sp);
	}

	/**
	 * 
	 * @param numberOfExtraSlots
	 *            Number of extra
	 * @param masterBestia
	 * @param bestias
	 */
	public BestiaInfoMessage(AccountMessage msg, PlayerBestia bestia, boolean isMaster) {
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

	public void setIsMaster(boolean isMaster) {
		this.isMaster = isMaster;
	}

	public PlayerBestia getBestia() {
		return bestia;
	}

	/**
	 * Returns the unique entity id of this bestia.
	 * 
	 * @return The entity ID of this bestia.
	 */
	public long getEntityId() {
		return entityId;
	}

	public void setBestia(PlayerBestia bestia, StatusPoints statusPoints) {
		if (bestia == null) {
			throw new IllegalArgumentException("bestia can not be null.");
		}
		if (statusPoints == null) {
			throw new IllegalArgumentException("statusPoints can not be null.");
		}
		this.bestia = bestia;
		this.statusPoints = statusPoints;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	public void setStatusPoints(StatusPoints statusPoints) {
		this.statusPoints = statusPoints;
	}

	@Override
	public String toString() {
		return String.format("BestiaInfoMessage[accId: %d, isMaster: %b, bestia: %s, statusPoints: %s]", getAccountId(),
				isMaster, bestia.toString(), statusPoints.toString());
	}
}
