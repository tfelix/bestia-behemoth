package net.bestia.zoneserver.ecs;

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

/**
 * Manages the communication towards the ECS system. Since this communication must somehow work asyncronously we store
 * messages inside this {@link ECSInputController} and the ECS will fetch them as its ticking. It will question the
 * InputController if it holds messages regarding a particular bestia and execute it in ECS world.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ECSInputController {

	private final static Logger log = LogManager.getLogger(ECSInputController.class);

	/**
	 * Callback which can be used to get notified about changed in this
	 * 
	 * @author Thomas
	 *
	 */
	public interface InputControllerCallback {
		public void removedBestia(long accId, int bestiaId);

		public void removedAccount(long accountId);

		public void addedAccount(long accountId);

		public void addedBestia(long accId, int bestiaId);
	}

	private final Map<Long, Set<PlayerBestiaManager>> activeBestias = new HashMap<>();
	private final Map<Integer, Queue<InputMessage>> inputQueues = new HashMap<>();
	
	private final List<InputControllerCallback> callbacks = new ArrayList<>();

	public ECSInputController() {

	}

	/**
	 * Adds a callback to the {@link ECSInputController}. These callback are called if the according events are
	 * triggered.
	 * 
	 * @param callback
	 *            Callback to be added.
	 */
	public void addCallback(InputControllerCallback callback) {
		// Cant be added twice.
		if (callbacks.contains(callback)) {
			return;
		}
		callbacks.add(callback);
	}

	/**
	 * Triggers callback action.
	 * 
	 * @param accId
	 *            Account ID which was added.
	 */
	private void onAddedAccount(long accId) {
		for (InputControllerCallback c : callbacks) {
			c.addedAccount(accId);
		}
	}

	/**
	 * Triggers callback action.
	 * 
	 * @param accId
	 *            Account id of the removed account.
	 */
	private void onRemovedAccount(long accId) {
		for (InputControllerCallback c : callbacks) {
			c.removedAccount(accId);
		}
	}

	/**
	 * Triggers callback action.
	 * 
	 * @param accId
	 *            Account id from which the bestia was removed.
	 * @param bestiaId
	 *            Id of the removed bestia.
	 */
	private void onRemovedBestia(long accId, int bestiaId) {
		for (InputControllerCallback c : callbacks) {
			c.removedBestia(accId, bestiaId);
		}
	}

	/**
	 * Triggers callback action.
	 * 
	 * @param accId
	 *            Account id to which the bestia was added.
	 * @param bestiaId
	 *            Id of the added bestia.
	 */
	private void onAddedBestia(long accId, int bestiaId) {
		for (InputControllerCallback c : callbacks) {
			c.addedBestia(accId, bestiaId);
		}
	}

	@Deprecated
	public void registerAccount(long accId, List<PlayerBestiaManager> bestias) {
		log.trace("Registered account: {} with {} bestias.", accId, bestias.size());

		if (!activeBestias.containsKey(accId)) {
			activeBestias.put(accId, new HashSet<>());
			onAddedAccount(accId);
		}

		for (PlayerBestiaManager pbm : bestias) {
			addPlayerBestia(accId, pbm);
		}
	}

	/**
	 * Removes an account an all currently registered bestias from the system. Outstanding input messages not yet
	 * processed are lost.
	 * 
	 * @param accId
	 *            Account ID of the account to be removed.
	 */
	public void removeAccount(long accId) {
		// Remove all outstanding bestias.
		Set<PlayerBestiaManager> bestias = activeBestias.get(accId);
		
		if(bestias == null) {
			return;
		}
		
		// Helper set to avoid ConcurrentModificationException.
		final Set<PlayerBestiaManager> removalSet = new HashSet<>();
		removalSet.addAll(bestias);

		for (PlayerBestiaManager bestia : removalSet) {
			removePlayerBestia(accId, bestia);
		}
	}

	/**
	 * Removes a bestia from the input system. If the last bestia of an account is removed the account will be removed
	 * itself.
	 * 
	 * @param accId
	 *            Account id where the bestia belongs to.
	 * @param bestia
	 *            The {@link PlayerBestiaManager} to remove.
	 */
	public void removePlayerBestia(long accId, PlayerBestiaManager bestia) {

		final int bestiaId = bestia.getBestia().getId();

		// Remove messages.
		inputQueues.remove(bestiaId);
		activeBestias.get(accId).remove(bestia);
		onRemovedBestia(accId, bestiaId);
		log.trace("Removed bestia: {} from account: {}.", bestiaId, accId);

		if (activeBestias.get(accId).size() == 0) {
			// Remove account aswell.
			activeBestias.remove(accId);
			onRemovedAccount(accId);
			log.trace("Remove account: {}.", accId);
		}
	}

	/**
	 * Adds an bestia to an active account into this system. If the account was not set as active before it will be set
	 * active by calling this method.
	 * 
	 * @param accId
	 *            Account ID to add the active bestia too.
	 * @param pbm
	 *            The {@link PlayerBestiaManager} to add as active to this account.
	 */
	public void addPlayerBestia(long accId, PlayerBestiaManager pbm) {
		if (activeBestias.get(accId) == null) {
			List<PlayerBestiaManager> list = new ArrayList<>();
			list.add(pbm);
			registerAccount(accId, list);

		} else {
			activeBestias.get(accId).add(pbm);
			final int bestiaId = pbm.getBestia().getId();
			inputQueues.put(bestiaId, new LinkedList<>());
			onAddedBestia(accId, bestiaId);
			log.trace("Added bestia: {} to account: {}.", bestiaId, accId);
		}
	}

	/**
	 * Returns a set with all currently active bestias on this zone.
	 * 
	 * @param accId
	 *            Account Id.
	 * @return List with currently active bestias.
	 */
	public Set<PlayerBestiaManager> getActiveBestias(long accId) {
		return activeBestias.get(accId);
	}

	/**
	 * Queues a message for delivery inside the input queue. Silently fails if there is not account/player bestia active
	 * with the id the message is referring to.
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

	/**
	 * Returns the {@link PlayerBestiaManager} of the given bestiaId. If no bestia is active with this id null is
	 * returned.
	 * 
	 * TODO Hier kann man auch noch mal die accId rauswerfen und das analog nur über die bestiaId abfragen.
	 * 
	 * @param accId
	 *            The account id which holds the requested bestia.
	 * @param bestiaId
	 *            Player bestia id of the requests {@link PlayerBestiaManager}.
	 * @return The manager or null if no player bestia with this id is active.
	 */
	public PlayerBestiaManager getActiveBestia(long accId, int bestiaId) {
		if (!activeBestias.containsKey(accId)) {
			return null;
		}

		Set<PlayerBestiaManager> bestias = activeBestias.get(accId);

		for (PlayerBestiaManager playerBestiaManager : bestias) {
			if (playerBestiaManager.getBestia().getId() == bestiaId) {
				return playerBestiaManager;
			}
		}
		
		return null;
	}
}
