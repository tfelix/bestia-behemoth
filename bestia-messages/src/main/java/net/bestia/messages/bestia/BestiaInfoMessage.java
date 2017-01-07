package net.bestia.messages.bestia;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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

	@JsonProperty("eid")
	private long entityId;

	@JsonProperty("sp")
	private StatusPoints statusPoints;

	/**
	 * For Jackson.
	 */
	public BestiaInfoMessage() {
		// no op.
	}
	
	public BestiaInfoMessage(long accId, long entityId, PlayerBestia pb, StatusPoints sp) {
		super(accId);
		
		Objects.requireNonNull(pb);
		
		this.entityId = entityId;
		this.isMaster = pb.getMaster() != null;
		this.bestia = pb;
		this.statusPoints = Objects.requireNonNull(sp);		
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

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	@Override
	public String toString() {
		return String.format("BestiaInfoMessage[accId: %d, isMaster: %b, bestia: %s, statusPoints: %s]", getAccountId(),
				isMaster, bestia.toString(), statusPoints.toString());
	}
}
