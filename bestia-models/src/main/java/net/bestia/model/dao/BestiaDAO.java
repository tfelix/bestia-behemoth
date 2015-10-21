package net.bestia.model.dao;

import org.springframework.data.repository.CrudRepository;

import net.bestia.model.domain.Bestia;

public interface BestiaDAO extends CrudRepository<Bestia, Integer> {

	/**
	 * Finds a bestia by its database name.
	 * 
	 * @param databaseName
	 *            The bestia database name.
	 * @return The found {@link Bestia} or NULL.
	 */
	public Bestia findByDatabaseName(String databaseName);

}
