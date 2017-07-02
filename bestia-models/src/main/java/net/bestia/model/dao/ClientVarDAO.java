package net.bestia.model.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.domain.ClientVar;

@Repository("clientvarDao")
@Transactional(readOnly = true)
public interface ClientVarDAO extends CrudRepository<ClientVar, Long> {

	/**
	 * Searches for a client variable.
	 * 
	 * @param key
	 *            The key of the client var.
	 * @param accountId
	 *            The owning account id.
	 * @return Returns the found client variable or null if no was found.
	 */
	ClientVar findByKeyAndAccountId(String key, long accountId);

	/**
	 * Deletes a client variable identified by its key and the owning account
	 * id.
	 * 
	 * @param key
	 *            The key of the client var.
	 * @param accountId
	 *            The owning account id.
	 */
	void deleteByKeyAndAccountId(String key, long accountId);

	/**
	 * Counts the number of bytes used by a single account.
	 * 
	 * @param accountId
	 *            The account to find.
	 * @return
	 */
	 List<ClientVar> findByAccountId(long accountId);
}
