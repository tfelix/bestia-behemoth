package net.bestia.model.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.Account;

/**
 * AccountDAO for accessing the database in order to get {@link Account} objects using Hibernate.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Repository("accountDao")
public interface AccountDAO extends CrudRepository<Account, Long> {

	/**
	 * Searches an account for a provided email address. Since e-mails are unique only one {@link Account} is returned or
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
	@Query("FROM Account a WHERE a.master.name = :username")
	public Account findByUsername(@Param("username") String username);
}