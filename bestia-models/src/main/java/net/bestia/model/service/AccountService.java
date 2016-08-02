package net.bestia.model.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.PlayerBestia;

/**
 * Generates all the needed account services. Please be careful: This factory is
 * not threadsafe. Therefore each thread should have its own AccountService.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service
public class AccountService {

	private final static Logger log = LoggerFactory.getLogger(AccountService.class);

	private AccountDAO accountDao;
	private PlayerBestiaDAO playerBestiaDao;
	private BestiaDAO bestiaDao;

	public enum Master {
		/**
		 * DPS starter class.
		 */
		FIGHTER,
		/**
		 * Tank starter class.
		 */
		KNIGHT,
		/**
		 * Support class.
		 */
		SPIRITUAL
	}

	@Autowired
	public void setAccountDao(AccountDAO accountDao) {
		this.accountDao = accountDao;
	}

	@Autowired
	public void setPlayerBestiaDao(PlayerBestiaDAO playerBestiaDAO) {
		this.playerBestiaDao = playerBestiaDAO;
	}

	@Autowired
	public void setBestiaDao(BestiaDAO bestiaDAO) {
		this.bestiaDao = bestiaDAO;
	}

	/**
	 * Creates a completely new account. The username will be given to the
	 * bestia master. No other bestia mastia can have this name.
	 * 
	 * @param email
	 *            E-Mail to use.
	 * @param mastername
	 *            Username of the bestia master.
	 * @param password
	 *            Password for the account.
	 * @param starter
	 *            Choosen starter bestia.
	 * @return {@code TRUE} if the new account coule be created. {@code FALSE}
	 *         otherwise.
	 */
	public void createNewAccount(String email, String mastername, String password, Master starter) {
		if (mastername == null || mastername.isEmpty()) {
			throw new IllegalArgumentException("Mastername can not be null or empty.");
		}

		final Account account = new Account(email, password);

		// TODO das hier noch auslagern. Die aktivierung soll nur per
		// username/password anmeldung notwendig sein.
		account.setActivated(true);

		// TODO Starter ID durch ein Script ? bestimmen lassen. Außerdem
		// Eventcodes berücksichtigen.
		int starterId = 1;

		// Depending on the master get the offspring bestia.
		final Bestia origin = bestiaDao.findOne(starterId);
		if (origin == null) {
			log.error("Starter bestia with id {} could not been found.", starterId);
			throw new IllegalArgumentException("Starter bestia was not found.");
		}

		// Check if there is a bestia master with this name.
		final PlayerBestia existingMaster = playerBestiaDao.findMasterBestiaWithName(mastername);

		if (existingMaster != null) {
			log.warn("Can not create account. Master name already exists: {}", mastername);
			throw new IllegalArgumentException("A master with this name does already exist.");
		}

		// Create the bestia.
		final PlayerBestia masterBestia = new PlayerBestia(account, origin, BaseValues.getStarterIndividualValues());

		masterBestia.setName(mastername);
		masterBestia.setMaster(account);

		// Generate ID.
		try {
			accountDao.save(account);
			playerBestiaDao.save(masterBestia);

		} catch (JpaSystemException ex) {
			log.warn("Could not create account because of duplicate mail: {}", ex.getMessage(), ex);
			throw new IllegalArgumentException("Could not create account. Duplicate mail.", ex);
		}
	}

	/**
	 * Returns all the bestias under a given account id. This includes the
	 * bestia master aswell as "normal" bestias.
	 * 
	 * @param accId
	 * @return Returns the set of player bestia for a given account id or NULL
	 *         if this account does not exist.
	 */
	public Set<PlayerBestia> getAllBestias(long accId) {
		final Account account = accountDao.findOne(accId);

		if (account == null) {
			return null;
		}

		final Set<PlayerBestia> bestias = playerBestiaDao.findPlayerBestiasForAccount(accId);

		// Add master as well since its not listed as a "player bestia".
		bestias.add(account.getMaster());

		return bestias;
	}

}
