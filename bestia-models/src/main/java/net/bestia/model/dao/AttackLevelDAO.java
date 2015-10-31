package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.domain.AttackLevel;
import net.bestia.model.domain.Bestia;

public interface AttackLevelDAO extends GenericDAO<AttackLevel, Integer> {

	/**
	 * Return all usable attacks for a given Bestia. The attacks are sorted by
	 * level requirement in ascending order.
	 * 
	 * @param bestia
	 *            Return all attacks for this bestia and its requirements.
	 * @return All attacks of this bestia.
	 */
	public List<AttackLevel> getAllAttacksForBestia(Bestia bestia);

	/**
	 * Returns a list of AttackLevel for a bestia with the level specified. It
	 * will only return attacks which are usable for this bestia with this given
	 * level.
	 * 
	 * @param bestia
	 *            Attacks for this kind of bestia.
	 * @param currentLevel
	 *            Only returning attacks for level requirement lower or equal
	 *            then this number.
	 * @return All usable attacks by the given level number.
	 */
	// public List<AttackLevel> getUsableAttacksForBestia(Bestia bestia, int
	// currentLevel);
}
