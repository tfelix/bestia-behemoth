package net.bestia.model.dao;

import net.bestia.model.domain.Account;

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

	/**
	 * Checks the nickname of the designated master. If this a master with this nickname is found then the apropriate
	 * {@link Account} is returned.
	 * 
	 * @param username
	 * @return Account with the master with this nickname or {@code null}.
	 */
	public Account findByNickname(String username);

}