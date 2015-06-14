package net.bestia.model.dao;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
public abstract class GenericDAOHibernate<E, K extends Serializable> implements GenericDAO<E, K> {

	private SessionFactory sessionFactory;
	protected final Class<? extends E> daoType;

	@SuppressWarnings("unchecked")
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

	@SuppressWarnings("unchecked")
	@Override
	public E find(K id) {
		return (E) currentSession().get(daoType, id);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<E> list() {
		return currentSession().createCriteria(daoType).list();
	}
}
