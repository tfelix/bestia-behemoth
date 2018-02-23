package net.bestia.zoneserver.service;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import bestia.model.dao.AccountDAO;
import bestia.model.dao.ClientVarDAO;
import bestia.model.domain.Account;
import bestia.model.domain.ClientVar;

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

	private static final int MAX_DATA_ENTRY_LENGTH_BYTES = 1000;
	private static final int MAX_DATA_LENGH_TOTAL_BYTES = 100 * 1024;

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

	/**
	 * Returns the number of bytes used in total by the given account id.
	 * 
	 * @param accId
	 *            The account ID to look up.
	 * @return The number of bytes used by this account.
	 */
	public int getBytesUsedByAccount(long accId) {
		List<ClientVar> cvars = cvarDao.findByAccountId(accId);
		return cvars.stream()
				.map(ClientVar::getDataLength)
				.mapToInt(Integer::intValue)
				.sum();
	}

	/**
	 * Finds the cvar associated with this account id and the key.
	 * 
	 * @param accId
	 *            The account id.
	 * @param key
	 *            A key.
	 * @return The associated cvar variable.
	 */
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
		Objects.requireNonNull(data);

		if (data.length() > MAX_DATA_ENTRY_LENGTH_BYTES) {
			final String errMsg = String.format("Data can not be longer then %d bytes.", MAX_DATA_ENTRY_LENGTH_BYTES);
			throw new IllegalArgumentException(errMsg);
		}

		if (getBytesUsedByAccount(accountId) + data.length() >= MAX_DATA_LENGH_TOTAL_BYTES) {
			final String errMsg = String.format("Max data stored can not be longer then %d bytes.",
					MAX_DATA_LENGH_TOTAL_BYTES);
			throw new IllegalArgumentException(errMsg);
		}

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
