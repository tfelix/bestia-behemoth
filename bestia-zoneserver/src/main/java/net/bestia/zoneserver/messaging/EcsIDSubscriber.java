package net.bestia.zoneserver.messaging;

import java.util.Set;
import java.util.function.Predicate;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.ecs.ECSCommandFactory;

public class EcsIDSubscriber implements Predicate<Message> {
	
	private final Set<String> messageIds;
	
	public EcsIDSubscriber(ECSCommandFactory cmdFactory) {
		
		messageIds = cmdFactory.getRegisteredMessageIds();
	}

	@Override
	public boolean test(Message t) {
		// TODO Auto-generated method stub
		return false;
	}
	
	

}
