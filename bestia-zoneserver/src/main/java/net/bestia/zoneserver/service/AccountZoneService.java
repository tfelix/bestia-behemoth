package net.bestia.zoneserver.service;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.service.AccountService;
import net.bestia.zoneserver.configuration.CacheConfiguration;

/**
 * Extends the normal {@link AccountService} with additional methods making only
 * sense in the zone server context like query the online bestias.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class AccountZoneService extends AccountService {

	private AccountDAO accountDao;
	private CacheManager<Long, String> clientCache;

	@Autowired
	public void setClientCache(
			@Qualifier(CacheConfiguration.CLIENT_CACHE) CacheManager<Long, String> clientCache) {
		this.clientCache = clientCache;
	}

	@Override
	@Autowired
	public void setAccountDao(AccountDAO accountDao) {
		this.accountDao = Objects.requireNonNull(accountDao);
		super.setAccountDao(accountDao);
	}

	/**
	 * Returns accounts via their username (bestia master name), but only if
	 * they are online. If the account is currently not logged in then null is
	 * returned.
	 * 
	 * @param username
	 *            The bestia master name to look for.
	 * @return The {@link Account} of this bestia master or null if the name
	 *         does not exist or the account is not online.
	 */
	public Account getOnlineAccountByName(String username) {
		Objects.requireNonNull(username);
		final Account acc = accountDao.findByUsername(username);

		if (acc == null) {
			return null;
		}

		// Check if this account is online.
		if (!clientCache.containsKey(acc.getId())) {
			return null;
		}

		return acc;
	}

}
