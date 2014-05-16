package net.bestia.core.persist;

import java.io.Serializable;
import java.util.List;

import org.hibernate.Query;


public interface GenericDAO<T, ID extends Serializable> {

    public void save(T entity);

    public void merge(T entity);

    public void delete(T entity);

    public List<T> findMany(Query query);

    public T findOne(Query query);

    public List<T> findAll(Class clazz);

    public T findByID(Class clazz, ID id);
}
