package net.bestia.zoneserver.ecs.manager;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.routing.MessageProcessor;

/**
 * This system hooks into the ECS and into the bestia messaging system. It will
 * accuire messages in a thread save manner and will be executed once a message
 * is waiting.
 * 
 * @author Thomas
 *
 */
@Wire
public abstract class MessageProcessSystem extends BaseSystem implements MessageProcessor {

	@Wire
	protected CommandContext ctx;

	private final Queue<Message> msgQueue = new ConcurrentLinkedQueue<>();

	public MessageProcessSystem() {

	}

	@Override
	protected void initialize() {
		super.initialize();

	}

	@Override
	protected boolean checkProcessing() {
		return !msgQueue.isEmpty();
	}

	@Override
	protected void processSystem() {

		while (msgQueue.peek() != null) {
			// We spawn the bestia.
			final Message msg = msgQueue.poll();
			handleMessage(msg);
		}
	}
	
	protected abstract void handleMessage(Message msg);

	@Override
	public void processMessage(Message msg) {
		msgQueue.add(msg);
	}

}
