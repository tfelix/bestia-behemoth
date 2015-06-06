package net.bestia.model.dao;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MemoryDao<E, K> implements GenericDAO<E, K>{
	
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
		if(entities.isEmpty()) {
			return null;
		}
		// just return the first one sice we are not using any keys ATM
		return entities.get(0);
	}

	@Override
	public List<E> list() {
		return entities;
	}

	

}
