package net.bestia.model.service;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.dao.AttackLevelDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.AttackLevel;
import net.bestia.model.domain.PlayerBestia;

@Transactional
@Service("PlayerBestiaService")
public class PlayerBestiaService {

	private final static Logger log = LogManager.getLogger(InventoryService.class);

	private PlayerBestiaDAO playerBestiaDao;
	private AttackLevelDAO attackLevelDao;

	@Autowired
	public void setPlayerBestiaDao(PlayerBestiaDAO playerBestiaDao) {
		this.playerBestiaDao = playerBestiaDao;
	}

	@Autowired
	public void setAttackLevelDao(AttackLevelDAO attackLevelDao) {
		this.attackLevelDao = attackLevelDao;
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

		final PlayerBestia playerBestia = playerBestiaDao.find(playerBestiaId);

		// Get list of attacks for this bestia.
		final List<AttackLevel> knownAttacks = attackLevelDao.getAllAttacksForBestia(playerBestia.getId());

		final List<Integer> knownAttackIds = knownAttacks.stream()
				.map((x) -> x.getAttack().getId())
				.collect(Collectors.toList());

		// Check if the bestia can actually learn the attacks and does know
		// them.
		int slot = 1;
		for (Integer atkId : attackIds) {
			if (!knownAttackIds.contains(atkId)) {
				log.error("PlayerBestia {} can not learn the attack id: {}.", playerBestiaId, atkId);
				throw new IllegalArgumentException("Bestia can not learn the attack.");
			}

			final AttackLevel atk = knownAttacks.stream()
					.filter((x) -> x.getAttack().getId() == atkId)
					.findFirst()
					.get();

			if (playerBestia.getLevel() < atk.getMinLevel()) {
				log.error("PlayerBestia {} level too low. Must be at least {}, is: {}.", playerBestia.getId(),
						atk.getMinLevel(), playerBestia.getLevel());
				throw new IllegalArgumentException("Bestia can not learn the attack.");
			}

			switch (slot) {
			case 1:
				playerBestia.setAttack1(atk.getAttack());
				break;
			case 2:
				playerBestia.setAttack2(atk.getAttack());
				break;
			case 3:
				playerBestia.setAttack3(atk.getAttack());
				break;
			case 4:
				playerBestia.setAttack4(atk.getAttack());
				break;
			case 5:
				playerBestia.setAttack5(atk.getAttack());
				break;
			default:
				// no op.
				break;
			}

			slot++;
		}
	}

	/**
	 * Returns all attacks for a certain player bestia with the given player
	 * bestia id.
	 * 
	 * @param playerBestiaId
	 * @return
	 */
	public List<AttackLevel> getAllAttacksForPlayerBestia(int playerBestiaId) {
		final PlayerBestia pb = playerBestiaDao.find(playerBestiaId);
		return attackLevelDao.getAllAttacksForBestia(pb.getOrigin().getId());
	}

}
