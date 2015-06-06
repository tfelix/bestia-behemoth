package net.bestia.model.dao;

import java.util.List;


public interface GenericDAO<E, K> {

    void save(E entity);

    void delete(E entity);
    
    E find(K key);
    
    List<E> list();
}
