package net.bestia.model.dao;

import java.io.Serializable;
import java.util.List;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import java.lang.reflect.ParameterizedType;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
public abstract class GenericDAOHibernate<E, K> implements GenericDAO<E, K> {

	private SessionFactory sessionFactory;
	protected final Class<? extends E> daoType;

	public GenericDAOHibernate() {
		daoType = (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}
	
	@Autowired
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
	
	protected Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

	@Override
	public void save(E entity) {
		currentSession().saveOrUpdate(entity);
	}

	@Override
	public void delete(E entity) {
		currentSession().delete(entity);
	}

	@Override
	public E find(K id) {
		//return (E) currentSession().
		return null;
	}
}
