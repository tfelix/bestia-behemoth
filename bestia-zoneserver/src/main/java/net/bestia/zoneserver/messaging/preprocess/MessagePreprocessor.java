package net.bestia.zoneserver.messaging.preprocess;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;

public abstract class MessagePreprocessor {
	
	protected final CommandContext ctx;
	
	public MessagePreprocessor(CommandContext ctx) {
		if(ctx == null) {
			throw new IllegalArgumentException("CommandContext can not be null.");
		}
		
		this.ctx = ctx;
	}
	
	public abstract Message process(Message message);

}
