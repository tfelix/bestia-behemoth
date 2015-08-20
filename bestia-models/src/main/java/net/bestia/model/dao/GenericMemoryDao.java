package net.bestia.model.dao;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic in-memory DAO. It does not use an actual database backend. It holds
 * all objects inside itself and starts as an empty DAO.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 * @param <E>
 *            Entity type.
 * @param <K>
 *            Key type of the entity.
 */
public class GenericMemoryDao<E, K> implements GenericDAO<E, K> {

	private List<E> entities = new ArrayList<E>();

	@Override
	public void save(E entity) {
		entities.add(entity);
	}

	@Override
	public void delete(E entity) {
		entities.remove(entity);
	}

	@Override
	public E find(K key) {
		if (entities.isEmpty()) {
			return null;
		}
		// just return the first one sice we are not using any keys ATM
		return entities.get(0);
	}

	@Override
	public List<E> list() {
		return entities;
	}

	@Override
	public void update(E entity) {
		if (!entities.contains(entity)) {
			return;
		}
		entities.remove(entity);
		entities.add(entity);
	}

}
