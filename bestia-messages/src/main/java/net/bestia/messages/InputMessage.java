package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Special class for player inputs which should not generate a server command.
 * Instead the input is directed into the EC system of the zone which is
 * responsible for the given player bestia. Some sanity checks are performed
 * like if the account is really the owner of the bestia wished to control.
 * Other than that the ECS is responsible for performing the actions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class InputMessage extends AccountMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;
	
	private static final String MSG_PATH_ACCOUNT_BESTIA = "account/%d/bestia/%d"; 

	@JsonProperty("pbid")
	private int playerBestiaId = 0;

	/**
	 * Std. Ctor. for deserialization purposes.
	 */
	public InputMessage() {

	}

	/**
	 * Ctor. For initializing this class directly with values.
	 * 
	 * @param msg
	 *            The {@link Message} to be based upon.
	 * @param pbid
	 *            The player bestia to which this input message shall be
	 *            addressed.
	 */
	public InputMessage(AccountMessage msg, int pbid) {
		super(msg);
		setPlayerBestiaId(pbid);
	}

	public InputMessage(long accId, int pbid) {
		this.setAccountId(accId);
		this.setPlayerBestiaId(pbid);
	}

	public InputMessage(InputMessage msg) {
		super(msg);
		this.setAccountId(msg.getAccountId());
		this.setPlayerBestiaId(msg.getPlayerBestiaId());
	}

	/**
	 * Gets the player bestia id for which this message is meant. Not all
	 * messages are subject to a selected player bestia thus this field can
	 * contain the ID 0 which is reserved for this case.
	 * 
	 * @return
	 */
	@JsonIgnore
	public int getPlayerBestiaId() {
		return playerBestiaId;
	}

	/**
	 * Sets the player bestia id if this message.
	 * 
	 * @param playerBestiaId
	 *            The id of the player bestia.
	 */
	public void setPlayerBestiaId(int playerBestiaId) {
		this.playerBestiaId = playerBestiaId;
	}

	/**
	 * Helper method. Can be used to subscribe to messages which are directed 
	 * 
	 * @return A message path designated to reach zoneserver on which a certain
	 *         user is connected.
	 */
	public static String getInputMessagePath(long accountId, int bestiaId) {
		return String.format(MSG_PATH_ACCOUNT_BESTIA, accountId, bestiaId);
	}
}
