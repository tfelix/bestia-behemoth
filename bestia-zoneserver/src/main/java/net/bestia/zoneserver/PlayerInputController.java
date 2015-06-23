package net.bestia.zoneserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.bestia.messages.InputMessage;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayerInputController {

	private final static Logger log = LogManager.getLogger(PlayerInputController.class);

	public interface PlayerRegisterCallback {
		public void removedBestia(long accId, int bestiaId);

		public void removedAccount(long accountId);

		public void addedAccount(long accountId);

		public void addedBestia(long accId, int bestiaId);
	}

	private final Map<Long, Set<Integer>> activeBestias = new HashMap<>();
	private final Map<Integer, Queue<InputMessage>> inputQueues = new HashMap<>();
	private final PlayerRegisterCallback callback;

	public PlayerInputController(PlayerRegisterCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("Callback can not be null.");
		}

		this.callback = callback;
	}

	public void registerAccount(long accId, List<PlayerBestiaManager> bestias) {
		log.trace("Registered account: {} with {} bestias.", accId, bestias.size());

		if (!activeBestias.containsKey(accId)) {
			activeBestias.put(accId, new HashSet<>());
			callback.addedAccount(accId);
		}

		for (PlayerBestiaManager pbm : bestias) {
			addPlayerBestia(accId, pbm);
		}
	}

	public void removeAccount(long accId) {	
		// Remove all outstanding bestias.
		Set<Integer> bestias = activeBestias.get(accId);
		
		for (Integer bestiaId : bestias) {
			removePlayerBestia(accId, bestiaId);
		}
		
		activeBestias.remove(accId);
		callback.removedAccount(accId);
		log.trace("Remove account: {}.", accId);
	}
	
	public void removePlayerBestia(long accId, int bestiaId) {
		
		// Remove messages.
		inputQueues.remove(bestiaId);
		activeBestias.get(accId).remove(bestiaId);
		callback.removedBestia(accId, bestiaId);
		log.trace("Removed bestia: {} from account: {}.", bestiaId, accId);
		
		if(activeBestias.get(accId).size() == 0) {
			// Remove account aswell.
			removeAccount(accId);
		}
	}
	
	public void addPlayerBestia(long accId, PlayerBestiaManager pbm) {
		if(activeBestias.get(accId) == null) {
			List<PlayerBestiaManager> list = new ArrayList<>();
			list.add(pbm);
			registerAccount(accId, list);
			
		} else {
			activeBestias.get(accId).add(pbm.getBestia().getId());
			inputQueues.put(pbm.getBestia().getId(), new LinkedList<>());
			callback.addedBestia(accId, pbm.getBestia().getId());
			log.trace("Added bestia: {} to account: {}.", pbm.getBestia().getId(), accId);
		}		
	}

	/**
	 * Queues a message for delivery inside the input queue.
	 * 
	 * @param message
	 *            {@link InputMessage} from the player.
	 */
	public void sendInput(InputMessage message) {

		if (inputQueues.containsKey(message.getPlayerBestiaId())) {
			inputQueues.get(message.getPlayerBestiaId()).add(message);
		}

	}

	/**
	 * Returns the input queue for the outstanding bestia messages.
	 * 
	 * @param playerBestiaId
	 *            Bestia ID for the outstanding messages.
	 * @return A queue with the {@link InputMessage}s or {@code null} if no bestia with this ID is registered at the
	 *         controller.
	 */
	public Queue<InputMessage> getInput(int playerBestiaId) {
		if (!inputQueues.containsKey(playerBestiaId)) {
			return null;
		}
		return inputQueues.get(playerBestiaId);
	}
}
