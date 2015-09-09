package net.bestia.zoneserver.ecs;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.bestia.messages.InputMessage;

/**
 * Manages the communication towards the ECS system. Since this communication must somehow work asynchronously we store
 * messages inside this {@link InputController2} and the ECS will fetch them as its ticking. It will question the
 * InputController if it holds messages regarding a particular bestia and execute it in ECS world.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InputController2 {
	
	private final Map<Integer, Queue<InputMessage>> inputQueues = new ConcurrentHashMap<>();

	public InputController2() {

	}

	public void registerQueue(Integer playerBestiaId) {
		if(inputQueues.containsKey(playerBestiaId)) {
			return;
		}
		
		inputQueues.put(playerBestiaId, new ConcurrentLinkedQueue<InputMessage>());
	}
	
	public void unregisterQueue(Integer playerBestiaId) {
		inputQueues.remove(playerBestiaId);
	}
	
	/**
	 * Queues a message for delivery to a specific bestia inside the input queue. Silently fails if there is not
	 * account/player bestia active with the id the message is referring to.
	 * 
	 * @param message
	 *            {@link InputMessage} from the player.
	 * @return
	 */
	public boolean sendInput(InputMessage message) {

		if (inputQueues.containsKey(message.getPlayerBestiaId())) {
			inputQueues.get(message.getPlayerBestiaId()).add(message);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Returns one of the pending {@link InputMessage}s for the given playerBesitaId. If no messages are waiting in the
	 * queue {@code NULL} will be returned.
	 * 
	 * @param playerBestiaId
	 *            The PlayerBestiaId of the bestia which InputMessages are polled.
	 * @return A InputMessage or NULL of the queue is empty.
	 */
	public InputMessage getNextInputMessage(int playerBestiaId) {
		if (!inputQueues.containsKey(playerBestiaId)) {
			return null;
		}
		final Queue<InputMessage> result = inputQueues.get(playerBestiaId);
		return result.poll();
	}
}
