package net.bestia.messages.bestia;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.domain.PlayerBestia;

/**
 * This message is send to the client to trigger a initial synchronization about
 * all possessed bestias and the bestia master. Contains general information
 * about the bestias, their status and their position on the map. Player bestia
 * itself can be null inside this message to save bandwidth if only status value
 * have changed.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaInfoMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "bestia.info";

	@JsonProperty("im")
	private boolean isMaster;

	@JsonProperty("b")
	private PlayerBestia bestia;

	/**
	 * For Jackson.
	 */
	protected BestiaInfoMessage() {
		// no op.
	}

	public BestiaInfoMessage(long accId, long entityId, PlayerBestia pb) {
		super(accId, entityId);

		Objects.requireNonNull(pb);

		this.isMaster = pb.getMaster() != null;
		this.bestia = pb;
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

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("BestiaInfoMessage[accId: %d, isMaster: %b, bestia: %s]",
				getAccountId(),
				isMaster, bestia.toString());
	}

	@Override
	public BestiaInfoMessage createNewInstance(long accountId) {
		return new BestiaInfoMessage(accountId, getEntityId(), getBestia());
	}
}
