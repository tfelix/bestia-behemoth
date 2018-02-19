package bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import bestia.model.domain.Bestia;

@Repository("bestiaDao")
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
