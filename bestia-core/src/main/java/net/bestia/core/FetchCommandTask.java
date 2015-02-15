package net.bestia.core;

import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.core.command.Command;
import net.bestia.core.command.CommandFactory;
import net.bestia.core.message.Message;

public class FetchCommandTask implements Callable<Command> {
	
	private final static Logger log = LogManager.getLogger(FetchCommandTask.class);
	
	private final CommandFactory cmdFactory;
	private final Message message;
	
	public FetchCommandTask(CommandFactory cmdFactory, Message message) {
		this.cmdFactory = cmdFactory;
		this.message = message;
	}


	@Override
	public Command call() throws Exception {
		log.trace("Entering FetchCommandTask.call(). {}", message);
		Command cmd = cmdFactory.getCommand(message);
		return cmd;
	}

}
