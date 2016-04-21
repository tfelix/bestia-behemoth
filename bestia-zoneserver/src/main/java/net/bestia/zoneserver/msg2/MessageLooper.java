package net.bestia.zoneserver.msg2;

import java.util.function.Predicate;

import net.bestia.messages.Message;
import net.bestia.zoneserver.messaging.MessageHandler;

/**
 * High level message handling. There will be some kind of priorization of
 * message handling. So message handler can be added with a priorization. This
 * can be useful if there needs to be some kind of preprozessing.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageLooper {

	public void handleMessage(Message msg) {
		// TODO Auto-generated method stub
		
	}


	public void subscribe(String messageId, MessageHandler handler) {
		// TODO Auto-generated method stub
		
	}


	public void unsubscribe(String messageId, MessageHandler handler) {
		// TODO Auto-generated method stub
		
	}


	public void subscribe(Predicate<Message> predicate, MessageHandler handler) {
		// TODO Auto-generated method stub
		
	}


	public void unsubscribe(Predicate<Message> predicate, MessageHandler handler) {
		// TODO Auto-generated method stub
		
	}
	
	

}
