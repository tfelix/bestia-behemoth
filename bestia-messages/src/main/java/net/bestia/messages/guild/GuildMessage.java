package net.bestia.messages.guild;

import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.Guild;

import java.util.Objects;

/**
 * Contains guild information.
 * 
 * @author Thomas Felix
 *
 */
public class GuildMessage extends JsonMessage {
	
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "guild.info";
	
	private Guild guild;

	private GuildMessage() {
		super(0);
	}

	public GuildMessage(long accId) {
		super(accId);
		// no op.
	}

	public GuildMessage(long accountId, Guild guild) {
		super(accountId);
		
		this.guild = Objects.requireNonNull(guild);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new GuildMessage(getAccountId(), guild);
	}	
}
