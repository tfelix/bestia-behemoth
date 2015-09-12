package net.bestia.zoneserver.ecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import net.bestia.zoneserver.manager.PlayerBestiaManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the communication towards the ECS system. Since this communication must somehow work asynchronously we store
 * messages inside this {@link BestiaRegister} and the ECS will fetch them as its ticking. It will question the
 * InputController if it holds messages regarding a particular bestia and execute it in ECS world.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaRegister {

	private final static Logger log = LogManager.getLogger(BestiaRegister.class);

	/**
	 * Callback which can be used to get notified about changed in this
	 *
	 */
	public interface InputControllerCallback {
		public void removedBestia(long accId, int bestiaId);

		public void removedAccount(long accountId);

		public void addedAccount(long accountId);

		public void addedBestia(long accId, int bestiaId);
	}

	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final Map<Long, Set<PlayerBestiaManager>> spawnedBestias = new HashMap<>();
	private final Map<Long, Integer> activeBestias = new HashMap<>();

	private final List<InputControllerCallback> callbacks = new ArrayList<>();

	public BestiaRegister() {

	}

	/**
	 * Adds a callback to the {@link BestiaRegister}. These callback are called if the according events are triggered.
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

		final int bestiaId = bestia.getPlayerBestiaId();

		// Remove messages.
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
		final int bestiaId = pbm.getPlayerBestiaId();
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
	 * Returns the {@link PlayerBestiaManager} of the given bestiaId. If no bestia is active with this id null is
	 * returned.
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
			if (playerBestiaManager.getPlayerBestiaId() == bestiaId) {

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
		lock.readLock().lock();
		try {
			if (!activeBestias.containsKey(accId)) {
				return 0;
			}

			return activeBestias.get(accId);
		} finally {
			lock.readLock().unlock();
		}
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
		lock.writeLock().lock();
		activeBestias.remove(accountId);
		lock.writeLock().unlock();
	}
}
