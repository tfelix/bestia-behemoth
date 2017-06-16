package net.bestia.model.dao;

import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.ScriptVar;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * DAO to access the {@link ScriptVar} models.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Repository("scriptVarDao")
public interface ScriptVarDAO extends CrudRepository<ScriptVar, Long> {

	/**
	 * Finds a {@link ScriptVar} which is linked to an {@link Account} and a
	 * script id.
	 * 
	 * @param key
	 *            The ID of the script.
	 * @param account
	 *            The account linked to this script.
	 * @return The found {@link ScriptVar} or null if none was found.
	 */
	@Query("SELECT sv FROM ScriptVar sv WHERE sv.nameId = :key AND sv.account = :acc")
	public ScriptVar findByNameId(@Param("key") String key, @Param("acc") Account account);

	/**
	 * Finds the {@link ScriptVar} whichi s linked to an {@link Account}, a
	 * {@link PlayerBestia} and the script id.
	 * 
	 * @param key
	 *            The ID of the script.
	 * @param account
	 *            The {@link Account} linked to the script var.
	 * @param playerBestia
	 *            The {@link PlayerBestia} linked to the script var.
	 * @return
	 */
	@Query("SELECT sv FROM ScriptVar sv WHERE sv.nameId = :key AND sv.account = :acc AND sv.playerBestia.id = :pb")
	public ScriptVar findByNameId(@Param("key") String key, @Param("acc") Account account,
			@Param("pb") PlayerBestia bestia);

	/**
	 * Deletes all {@link ScriptVar}s linked to this given bestia.
	 * 
	 * @param bestia
	 *            The {@link PlayerBestia} to delete all linked
	 *            {@link ScriptVar}s.
	 */
	//public void deleteByPlayerBestia(PlayerBestia bestia);

}
