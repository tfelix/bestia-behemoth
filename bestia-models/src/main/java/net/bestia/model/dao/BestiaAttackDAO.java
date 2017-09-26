package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.domain.BestiaAttack;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository("bestiaAttackDao")
public interface BestiaAttackDAO extends org.springframework.data.repository.Repository<BestiaAttack, Integer> {

	/**
	 * Return all usable attacks for a given Bestia. The attacks are sorted by
	 * level requirement in ascending order. If not attacks where found an empty
	 * list is returned.
	 * 
	 * @param bestia
	 *            Return all attacks for this bestia and its requirements.
	 * @return All attacks of this bestia. If no attack was found an empty list
	 *         is returned.
	 */
	@Query("FROM BestiaAttack ba WHERE ba.bestia.id = :bestiaId ORDER BY ba.minLevel ASC")
	public List<BestiaAttack> getAllAttacksForBestia(@Param("bestiaId") int bestiaId);
}
