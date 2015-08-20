package net.bestia.model.dao;

import java.util.List;

/**
 * This is a generic DAO interface for all other DAOs. It provides some basic
 * operations for retrieving and persisting data. These methods are
 * automatically implemented by the {@link GenericDAOHibernate} class and the
 * template parameter.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 * @param <E>
 *            Entity object.
 * @param <K>
 *            Database Key.
 */
public interface GenericDAO<E, K> {

	/**
	 * Save (or update) a new entity.
	 * 
	 * @param entity
	 */
	void save(E entity);

	/**
	 * Only update am already existing entity.
	 * 
	 * @param entity
	 */
	void update(E entity);

	/**
	 * Delete a given entity.
	 * 
	 * @param entity
	 */
	void delete(E entity);

	/**
	 * Find a given entity via a key.
	 * 
	 * @param key
	 *            Primary key of the entity to look for.
	 * @return The found entity or {@code null}.
	 */
	E find(K key);

	/**
	 * List all existing entities in the database.
	 * 
	 * @return A list of all entities.
	 */
	List<E> list();
}
