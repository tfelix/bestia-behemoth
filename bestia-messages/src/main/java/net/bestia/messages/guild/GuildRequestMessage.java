package net.bestia.messages.guild;

import net.bestia.messages.AccountMessage;

/**
 * Asks the server to provide details about the requested guild.
 * 
 * @author Thomas Felix
 *
 */
public class GuildRequestMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "guild.req";
	
	public int requestedGuildId;
	
	public GuildRequestMessage(long accId, int guildId) {
		super(accId);
		
		this.requestedGuildId = guildId;
	}
	
	public static String getMessageId() {
		return MESSAGE_ID;
	}
	
	public int getRequestedGuildId() {
		return requestedGuildId;
	}

	@Override
	public AccountMessage createNewInstance(long accountId) {
		return new GuildRequestMessage(accountId, getRequestedGuildId());
	}
}
