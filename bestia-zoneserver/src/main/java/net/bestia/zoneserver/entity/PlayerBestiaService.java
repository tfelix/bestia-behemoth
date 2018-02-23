package net.bestia.zoneserver.entity;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bestia.model.dao.AccountDAO;
import bestia.model.dao.BestiaAttackDAO;
import bestia.model.dao.PlayerBestiaDAO;
import bestia.model.domain.Account;
import bestia.model.domain.BestiaAttack;
import bestia.model.domain.PlayerBestia;
import bestia.model.domain.PlayerItem;

/**
 * The service for managing and editing of the player bestias.
 * 
 * @author Thomas Felix
 *
 */
@Service
@Transactional
public class PlayerBestiaService {

	private final static int NUM_ITEM_SLOTS = 5;
	private final static Logger LOG = LoggerFactory.getLogger(PlayerBestiaService.class);

	private final AccountDAO accountDao;
	private final PlayerBestiaDAO playerBestiaDao;
	private final BestiaAttackDAO attackDao;

	@Autowired
	public PlayerBestiaService(AccountDAO accountDao, 
			PlayerBestiaDAO playerBestiaDao, 
			BestiaAttackDAO attackDao) {

		this.accountDao = Objects.requireNonNull(accountDao);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
		this.attackDao = Objects.requireNonNull(attackDao);
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
		return attackDao.getAllAttacksForBestia(pb.getOrigin().getId());
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
		final PlayerItem[] checkedItems = new PlayerItem[NUM_ITEM_SLOTS];

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
		final PlayerBestia master = getMaster(accId);
		
		if(master != null) {
			bestias.add(master);
		}

		return bestias;
	}

	/**
	 * Returns the player bestia with the given id or null.
	 * 
	 * @param playerBestiaId
	 * @return
	 */
	public PlayerBestia getPlayerBestia(long playerBestiaId) {
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

	/**
	 * Saves the given player bestia into the database.
	 * 
	 * @param playerBestia
	 *            The bestia to save into the database.
	 */
	public void save(PlayerBestia playerBestia) {
		Objects.requireNonNull(playerBestia);

		LOG.debug("Persisting player bestia: {}.", playerBestia);
		playerBestiaDao.save(playerBestia);

	}
}
