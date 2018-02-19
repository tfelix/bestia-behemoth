package bestia.messages.bestia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import bestia.messages.JsonMessage;

/**
 * This message is send to the client to trigger a initial synchronization about
 * all possessed bestias and the bestia master. Contains general information
 * about the bestias, their status and their position on the map. Player bestia
 * itself can be null inside this message to save bandwidth if only status value
 * have changed.
 * 
 * @author Thomas Felix
 *
 */
public class BestiaInfoMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "bestia.info";

	@JsonProperty("m")
	private long masterEid;

	private List<Long> bestiaEids = new ArrayList<>();
	
	/**
	 * Needed for MessageTypeIdResolver 
	 */
	private BestiaInfoMessage() {
		super(0);
	}

	public BestiaInfoMessage(long accId, long masterEntityId, Collection<Long> bestiaEntityIds) {
		super(accId);

		this.masterEid = masterEntityId;
		this.bestiaEids.addAll(bestiaEntityIds);
	}


	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	public long getMasterEid() {
		return masterEid;
	}
	
	public List<Long> getBestiaEids() {
		return bestiaEids;
	}

	@Override
	public String toString() {
		return String.format("BestiaInfoMessage[accId: %d, master: %d, bestias: %s]",
				getAccountId(), getMasterEid(), getBestiaEids());
	}

	@Override
	public BestiaInfoMessage createNewInstance(long accountId) {
		return new BestiaInfoMessage(accountId, getMasterEid(), getBestiaEids());
	}
}
