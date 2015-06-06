package net.bestia.model.dao;

import net.bestia.model.Account;

public interface AccountDAO extends GenericDAO<Account, Long> {

	/**
	 * Searches an account for a provided email address. Since emails are unique only one {@link Account} is returned or
	 * {@code null}.
	 * 
	 * @param email
	 *            Email adress to look for.
	 * @return Account if found or {@code null}.
	 */
	public Account findByEmail(String email);

}