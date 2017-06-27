package net.bestia.model.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.domain.Shortcut;

@Repository("shortcutDao")
@Transactional(readOnly = true)
public interface ShortcutDAO extends CrudRepository<Shortcut, Integer> {

	/**
	 * Selects all shortcuts which are registered to a bestia.
	 * 
	 * @param playerBestiaId
	 *            The player besta id.
	 * @return The shortcuts for this player bestia.
	 */
	List<Shortcut> findByPlayerBestiaId(long playerBestiaId);

	/**
	 * Selects all the shortcuts which have an account but no player bestia set.
	 * 
	 * @param accountId
	 *            The account ID to look up.
	 * @return All the account bound shortcuts.
	 */
	List<Shortcut> findByAccountIdAndPlayerBestiaIsNull(long accountId);

}
