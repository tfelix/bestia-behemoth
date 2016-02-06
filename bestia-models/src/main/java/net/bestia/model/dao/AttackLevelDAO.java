package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.domain.AttackLevel;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository("attackLevelDao")
public interface AttackLevelDAO extends org.springframework.data.repository.Repository<AttackLevel, Integer> {

	/**
	 * Return all usable attacks for a given Bestia. The attacks are sorted by
	 * level requirement in ascending order.
	 * 
	 * @param bestia
	 *            Return all attacks for this bestia and its requirements.
	 * @return All attacks of this bestia.
	 */
	@Query("FROM AttackLevel al WHERE al.bestia.id = :bestiaId order by al.minLevel asc")
	public List<AttackLevel> getAllAttacksForBestia(@Param("bestiaId") int bestiaId);
	
}
