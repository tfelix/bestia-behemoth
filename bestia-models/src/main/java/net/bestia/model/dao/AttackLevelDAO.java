package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.domain.AttackLevel;

public interface AttackLevelDAO extends GenericDAO<AttackLevel, Integer> {

	/**
	 * Return all usable attacks for a given Bestia. The attacks are sorted by
	 * level requirement in ascending order.
	 * 
	 * @param bestia
	 *            Return all attacks for this bestia and its requirements.
	 * @return All attacks of this bestia.
	 */
	public List<AttackLevel> getAllAttacksForBestia(int bestiaId);
	
}
