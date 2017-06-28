package net.bestia.zoneserver.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.ClientVarDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.ClientVar;

/**
 * Service for managing and saving shortcuts coming from the clients to the
 * server.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ClientVarService {

	private static final Logger LOG = LoggerFactory.getLogger(ClientVarService.class);

	private final ClientVarDAO cvarDao;
	private final AccountDAO accDao;

	@Autowired
	public ClientVarService(ClientVarDAO cvarDao, AccountDAO accDao) {

		this.cvarDao = Objects.requireNonNull(cvarDao);
		this.accDao = Objects.requireNonNull(accDao);

	}

	/**
	 * Checks if the given account is the owner of the the variable.
	 * 
	 * @param accId
	 *            The account ID to check.
	 * @param key
	 *            The key of the var to check.
	 * @return TRUE if the account owns this variable. FALSE otherwise.
	 */
	public boolean isOwnerOfVar(long accId, String key) {
		final ClientVar cvar = cvarDao.findByKeyAndAccountId(Objects.requireNonNull(key), accId);
		return cvar != null;
	}

	/**
	 * Delete the cvar for the given account and key.
	 * 
	 * @param accId
	 *            The account id.
	 * @param key
	 *            The key of the cvar.
	 */
	public void delete(long accId, String key) {
		LOG.debug("Deleting cvar with accID: {} and key: {}.", accId, key);
		cvarDao.deleteByKeyAndAccountId(key, accId);
	}

	public ClientVar find(long accId, String key) {
		LOG.debug("Finding cvar with accID: {} and key: {}.", accId, key);
		final ClientVar cvar = cvarDao.findByKeyAndAccountId(Objects.requireNonNull(key), accId);
		return cvar;
	}

	/**
	 * Creates or updates client variable.
	 * 
	 * @param accountId
	 *            The account ID.
	 * @param key
	 *            The key of the variable to set or update.
	 * @param data
	 *            The data payload.
	 */
	public void set(long accountId, String key, String data) {

		ClientVar cvar = find(accountId, key);

		if (cvar != null) {
			cvar.setData(data);

		} else {

			// Cvar is not yet set. Just create one.
			final Account acc = accDao.findOne(accountId);
			if (acc == null) {
				throw new IllegalArgumentException("Account does not exist.");
			}

			cvar = new ClientVar(acc, key, data);
		}

		cvarDao.save(cvar);
	}
}
