package net.bestia.zoneserver.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaAttackDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.dao.PlayerItemDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.BestiaAttack;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.PlayerItem;
import net.bestia.zoneserver.entity.Entity;

/**
 * The service for managing and editing of the player bestias.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
@Transactional
public class PlayerBestiaService {

	private final static int NUM_ITEM_SLOTS = 5;
	private final static Logger LOG = LoggerFactory.getLogger(PlayerBestiaService.class);

	private AccountDAO accountDao;
	private PlayerBestiaDAO playerBestiaDao;
	private BestiaAttackDAO attackLevelDao;
	private PlayerItemDAO playerItemDao;

	@Autowired
	public void setPlayerBestiaDao(PlayerBestiaDAO playerBestiaDao) {
		this.playerBestiaDao = playerBestiaDao;
	}

	@Autowired
	public void setAttackLevelDao(BestiaAttackDAO attackLevelDao) {
		this.attackLevelDao = attackLevelDao;
	}

	@Autowired
	public void setPlayerItemDao(PlayerItemDAO playerItemDao) {
		this.playerItemDao = playerItemDao;
	}

	@Autowired
	public void setAccountDao(AccountDAO accountDao) {
		this.accountDao = accountDao;
	}

	/**
	 * Returns all attacks for a certain player bestia with the given player
	 * bestia id.
	 * 
	 * @param playerBestiaId
	 * @return A list of attacks usable by the given player bestia id.
	 */
	public List<BestiaAttack> getAllAttacksForPlayerBestia(long playerBestiaId) {
		LOG.trace("Retrieving all attacks for player bestia {}", playerBestiaId);
		final PlayerBestia pb = playerBestiaDao.findOne(playerBestiaId);
		return attackLevelDao.getAllAttacksForBestia(pb.getOrigin().getId());
	}

	/**
	 * The item ids are looked up and a reference is saved for the bestia. The
	 * player must have this item in his inventory.
	 * 
	 * @param playerBestiaId
	 * @param itemIds
	 * @return Returns the checked item shortcut slot array. If the bestia does
	 *         not own this item the slot will be null otherwise it will contain
	 *         the item.
	 */
	public PlayerItem[] saveItemShortcuts(long playerBestiaId, List<Integer> itemIds) {
		LOG.trace("Saving item shortcuts {} for player bestia {}", itemIds, playerBestiaId);

		if (itemIds.size() != 5) {
			throw new IllegalArgumentException(
					"The size of the item slot array must be 5. Fill empty slots with null.");
		}

		final PlayerBestia bestia = playerBestiaDao.findOne(playerBestiaId);
		final Set<Integer> nonNullIds = itemIds.stream().filter((x) -> x != null).collect(Collectors.toSet());
		final List<PlayerItem> foundItems = playerItemDao.findAllPlayerItemsForIds(nonNullIds);

		final PlayerItem[] checkedItems = new PlayerItem[NUM_ITEM_SLOTS];

		for (int i = 0; i < NUM_ITEM_SLOTS; i++) {

			final Integer id = itemIds.get(i);
			final PlayerItem item;

			if (id == null) {
				item = null;
			} else {
				item = foundItems.stream().filter((x) -> x.getItem().getId() == id).findFirst().orElse(null);
			}

			switch (i) {
			case 0:
				bestia.setItem1(item);
				checkedItems[0] = item;
				break;
			case 1:
				bestia.setItem2(item);
				checkedItems[1] = item;
				break;
			case 2:
				bestia.setItem3(item);
				checkedItems[2] = item;
				break;
			case 3:
				bestia.setItem4(item);
				checkedItems[3] = item;
				break;
			case 4:
				bestia.setItem5(item);
				checkedItems[4] = item;
				break;
			default:
				// no op.
				break;
			}
		}

		// Save.
		playerBestiaDao.save(bestia);

		return checkedItems;
	}

	/**
	 * Returns all the bestias under a given account id. This includes the
	 * bestia master as well as "normal" bestias.
	 * 
	 * @param accId
	 * @return Returns the set of player bestia for a given account id or an
	 *         empty set if this account does not exist.
	 */
	public Set<PlayerBestia> getAllBestias(long accId) {

		final Set<PlayerBestia> bestias = playerBestiaDao.findPlayerBestiasForAccount(accId);

		// Add master as well since its not listed as a "player bestia".
		bestias.add(getMaster(accId));

		return bestias;
	}

	/**
	 * Synchronizes and saves all the player bestia entities back to the
	 * database.
	 */
	public void updatePlayerBestias(Set<Entity> bestias) {
		
		//playerBestiaDao.save(bestias);
	}

	/**
	 * Returns the player bestia with the given id or null.
	 * 
	 * @param playerBestiaId
	 * @return
	 */
	public PlayerBestia getBestia(long playerBestiaId) {
		return playerBestiaDao.findOne(playerBestiaId);
	}

	/**
	 * Returns the master bestia for this given account id.
	 * 
	 * @param accountId
	 * @return The master bestia or NULL if the account does not extist.
	 */
	public PlayerBestia getMaster(long accountId) {
		final Account account = accountDao.findOne(accountId);

		if (account == null) {
			return null;
		}

		return account.getMaster();
	}
}
