package net.bestia.model.service;

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
import net.bestia.model.domain.AttackImpl;
import net.bestia.model.domain.BestiaAttack;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.PlayerItem;

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
	private final static Logger log = LoggerFactory.getLogger(InventoryService.class);

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
	 * Saves the given attacks to a bestia. There a checks in place in order to
	 * check the validity of this action. The bestia must be qualified in order
	 * to learn the attack: level constraints must be met and the attack must be
	 * learnable in general.
	 * 
	 * @param playerBestiaId
	 * @param attackIds
	 */
	public void saveAttacks(int playerBestiaId, List<Integer> attackIds) {
		// Some sanity checks.
		if (attackIds.size() > 5) {
			throw new IllegalArgumentException("Attacks can not exceed the length of 5 slots.");
		}

		final PlayerBestia playerBestia = playerBestiaDao.findOne(playerBestiaId);

		// Get list of attacks for this bestia.
		final List<BestiaAttack> knownAttacks = attackLevelDao.getAllAttacksForBestia(playerBestia.getOrigin().getId());

		final List<Integer> knownAttackIds = knownAttacks.stream()
				.map((x) -> x.getAttack().getId())
				.collect(Collectors.toList());

		// Check if the bestia can actually learn the attacks and does know
		// them.
		int slot = 1;
		for (Integer atkId : attackIds) {

			// Just delete the attack.
			if (atkId == 0) {
				setPlayerBestiaAttack(playerBestia, null, slot);
				slot++;
				continue;
			}

			if (!knownAttackIds.contains(atkId)) {
				log.error("PlayerBestia {} can not learn the attack id: {}.", playerBestiaId, atkId);
				throw new IllegalArgumentException("Bestia can not learn the attack.");
			}

			final BestiaAttack atk = knownAttacks.stream()
					.filter((x) -> x.getAttack().getId() == atkId)
					.findFirst()
					.get();

			if (playerBestia.getLevel() < atk.getMinLevel()) {
				log.error("PlayerBestia {} level too low. Must be at least {}, is: {}.", playerBestia.getId(),
						atk.getMinLevel(), playerBestia.getLevel());
				throw new IllegalArgumentException("Bestia can not learn the attack.");
			}
			setPlayerBestiaAttack(playerBestia, atk.getAttack(), slot);
			slot++;
		}
	}

	/**
	 * Helper method to set the attack.
	 * 
	 * @param playerBestia
	 *            The bestia whose attacks to be set.
	 * @param atk
	 *            The attack to set.
	 * @param slot
	 *            The slot to set the attack into.
	 */
	private void setPlayerBestiaAttack(PlayerBestia playerBestia, AttackImpl atk, int slot) {
		switch (slot) {
		case 1:
			playerBestia.setAttack1(atk);
			break;
		case 2:
			playerBestia.setAttack2(atk);
			break;
		case 3:
			playerBestia.setAttack3(atk);
			break;
		case 4:
			playerBestia.setAttack4(atk);
			break;
		case 5:
			playerBestia.setAttack5(atk);
			break;
		default:
			// no op.
			break;
		}
	}

	/**
	 * Returns all attacks for a certain player bestia with the given player
	 * bestia id.
	 * 
	 * @param playerBestiaId
	 * @return
	 */
	public List<BestiaAttack> getAllAttacksForPlayerBestia(int playerBestiaId) {
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
	public PlayerItem[] saveItemShortcuts(int playerBestiaId, List<Integer> itemIds) {

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
	 * Saves all given bestias back to the database.
	 */
	public void saveBestias(Set<PlayerBestia> bestias) {
		playerBestiaDao.save(bestias);
	}

	/**
	 * Returns the player bestia with the given id or null.
	 * 
	 * @param playerBestiaId
	 * @return
	 */
	public PlayerBestia getBestia(int playerBestiaId) {
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
