package net.bestia.zoneserver.ecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the communication towards the ECS system. Since this communication must somehow work asyncronously we store
 * messages inside this {@link InputController} and the ECS will fetch them as its ticking. It will question the
 * InputController if it holds messages regarding a particular bestia and execute it in ECS world.
 * 
 * TODO Das ding wird von mehreren Threads geshared. Mit read/write lock versehen.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InputController {

	private final static Logger log = LogManager.getLogger(InputController.class);

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

	private final Map<Long, Set<PlayerBestiaManager>> spawnedBestias = new ConcurrentHashMap<>();
	private final Map<Integer, Queue<Message>> inputQueues = new ConcurrentHashMap<>();
	private final Map<Long, Integer> activeBestias = new ConcurrentHashMap<>();

	private final List<InputControllerCallback> callbacks = new ArrayList<>();

	public InputController() {

	}

	/**
	 * Adds a callback to the {@link InputController}. These callback are called if the according events are triggered.
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

	/**
	 * Removes an account an all currently registered bestias from the system. Outstanding input messages not yet
	 * processed are lost.
	 * 
	 * @param accId
	 *            Account ID of the account to be removed.
	 */
	public void removeAccount(long accId) {

		// Remove all outstanding bestias.
		Set<PlayerBestiaManager> bestias = spawnedBestias.get(accId);

		if (bestias == null) {
			return;
		}

		// Helper set to avoid ConcurrentModificationException.
		final Set<PlayerBestiaManager> removalSet = new HashSet<>();
		removalSet.addAll(bestias);

		for (PlayerBestiaManager bestia : removalSet) {
			removePlayerBestia(accId, bestia);
		}

		// Remove possible active bestias.
		activeBestias.remove(accId);

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
		spawnedBestias.get(accId).remove(bestia);
		onRemovedBestia(accId, bestiaId);
		log.trace("Removed bestia: {} from account: {}.", bestiaId, accId);

		// If it was an active bestia. Remove it.
		if (activeBestias.containsKey(accId) && activeBestias.get(accId) == bestiaId) {
			activeBestias.remove(accId);
		}

		if (spawnedBestias.get(accId).size() == 0) {
			// Remove account aswell.
			spawnedBestias.remove(accId);
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

		if (spawnedBestias.get(accId) == null) {
			List<PlayerBestiaManager> list = new ArrayList<>();
			list.add(pbm);
			spawnedBestias.put(accId, new HashSet<>());
			onAddedAccount(accId);
			log.trace("Added account: {}.", accId);
		}

		spawnedBestias.get(accId).add(pbm);
		final int bestiaId = pbm.getBestia().getId();
		inputQueues.put(bestiaId, new LinkedList<>());
		onAddedBestia(accId, bestiaId);
		log.trace("Added bestia: {} to account: {}.", bestiaId, accId);

	}

	/**
	 * Returns a set with all currently active bestias for a given account id on this server.
	 * 
	 * @param accId
	 *            Account id.
	 * @return List with currently spawned bestias.
	 */
	public Set<PlayerBestiaManager> getSpawnedBestias(long accId) {

		final Set<PlayerBestiaManager> result = spawnedBestias.get(accId);
		return result;
	}

	/**
	 * Queues a message for delivery to a specific bestia inside the input queue. Silently fails if there is not
	 * account/player bestia active with the id the message is referring to.
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
	 * Queues a message for delivery. Like {@link sendInput}, since {@link InputMessage}s are helper to address a
	 * special bestia directly with normal messages this is not directly possible. Thus a playerBestiaId is needed to
	 * deliver the message. Apart from this the message processing system is not dependent upon a special playerBestiaId
	 * inside the message and can therefore handle normal messages as well.
	 * 
	 * @param message
	 *            Message to deliver.
	 * @param playerBestiaId
	 *            ID of the bestia to address this message to.
	 * @return boolean TRUE if delivery was possible. FALSE if there was no bestia spawned with this id (message could
	 *         not be delivered).
	 */
	public boolean sendInput(Message message, int playerBestiaId) {

		if (inputQueues.containsKey(playerBestiaId)) {
			inputQueues.get(playerBestiaId).add(message);
			return true;
		} else {
			return false;
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
	public Queue<Message> getInput(int playerBestiaId) {

		if (!inputQueues.containsKey(playerBestiaId)) {
			return null;
		}
		final Queue<Message> result = inputQueues.get(playerBestiaId);
		return result;
	}

	/**
	 * Returns the {@link PlayerBestiaManager} of the given bestiaId. If no bestia is active with this id null is
	 * returned.
	 * 
	 * TODO Hier kann man auch noch mal die accId rauswerfen und das analog nur über die bestiaId abfragen. Indem man
	 * das besser chached.
	 * 
	 * @param accId
	 *            The account id which holds the requested bestia.
	 * @param bestiaId
	 *            Player bestia id of the requests {@link PlayerBestiaManager}.
	 * @return The manager or null if no player bestia with this id is active.
	 */
	public PlayerBestiaManager getSpawnedBestia(long accId, int bestiaId) {

		if (!spawnedBestias.containsKey(accId)) {
			return null;
		}

		Set<PlayerBestiaManager> bestias = spawnedBestias.get(accId);

		for (PlayerBestiaManager playerBestiaManager : bestias) {
			if (playerBestiaManager.getBestia().getId() == bestiaId) {

				return playerBestiaManager;
			}
		}

		return null;
	}

	/**
	 * Returns the id of the active bestia for this account id. If no active bestia was found 0 is returned (which is no
	 * valid bestia id).
	 * 
	 * @param accId
	 *            Account id too look up the active bestia.
	 * @return The bestia id or 0 if no bestia was marked as active.
	 */
	public int getActiveBestia(long accId) {

		if (!activeBestias.containsKey(accId)) {
			return 0;
		}

		final int bestiaId = activeBestias.get(accId);

		return bestiaId;
	}

	/**
	 * Sets a active bestia for this account.
	 * 
	 * @param accId
	 * @param bestiaId
	 */
	public void setActiveBestia(long accId, int bestiaId) {

		// The bestia must be active.
		final Set<PlayerBestiaManager> pbm = spawnedBestias.get(accId);

		if (!pbm.stream().map(x -> x.getPlayerBestiaId()).collect(Collectors.toList()).contains(bestiaId)) {
			// Cannot mark active.
			return;
		}

		activeBestias.put(accId, bestiaId);
	}

	public void unsetActiveBestia(long accountId) {
		activeBestias.remove(accountId);
	}
}