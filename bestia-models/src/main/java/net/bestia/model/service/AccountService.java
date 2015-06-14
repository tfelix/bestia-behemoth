package net.bestia.model.service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.PlayerBestia;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates all the needed account services. Please be careful: This factory is not threadsafe. Therefore each thread
 * should have its own service factory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service("AccountService")
public class AccountService {

	private final static Logger log = LogManager.getLogger(AccountService.class);

	private AccountDAO accountDao;
	private PlayerBestiaDAO playerBestiaDao;
	private BestiaDAO bestiaDao;

	public enum Master {
		/*
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

	public AccountDAO getAccountDao() {
		return this.accountDao;
	}

	public BestiaDAO getBestiaDao() {
		return this.bestiaDao;
	}

	public PlayerBestiaDAO getPlayerBestiaDao() {
		return this.playerBestiaDao;
	}

	/**
	 * Returns an Account with the given id. Returns null if no account has been found.
	 * 
	 * @param accId
	 * @return
	 */
	public Account getAccount(long accId) {
		Account data = accountDao.find(accId);
		return data;
	}

	/**
	 * Creates a completely new account.
	 * 
	 * @param email
	 *            E-Mail to use.
	 * @param mastername
	 *            Username of the bestia master.
	 * @param password
	 *            Password for the account.
	 * @param starter
	 *            Choosen starter bestia.
	 * @return {@code TRUE} if the new account coule be created. {@code FALSE} otherwise.
	 */
	@Transactional
	public boolean createNewAccount(String email, String mastername, String password, Master starter) {
		Account account = new Account(email, password);
		// TODO das hier noch auslagern. Die aktivierung soll nur per username/password anmeldung notwendig sein.
		account.setActivated(true);
		
		// TODO Starter ID durch ein Script ? bestimmen lassen. Außerdem Eventcodes berücksichtigen.
		int starterId = 1;

		// Depending on the master get the offspring bestia.
		Bestia origin = bestiaDao.find(starterId);
		if(origin == null) {
			log.error("Starter bestia with id {} could not been found.", starterId);
			return false;
		}

		// Create the bestia.
		PlayerBestia masterBestia = new PlayerBestia(account, origin, BaseValues.getStarterIndividualValues());
		masterBestia.setName(mastername);
		
		account.setMaster(masterBestia);

		// Generate ID.
		accountDao.save(account);
		playerBestiaDao.save(masterBestia);
		// Save account again to set master id.
		accountDao.save(account);
		
		return true;
	}

}
