package net.bestia.core.persist;

import java.io.Serializable;
import java.util.List;

import javax.transaction.Transactional;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Transactional
public abstract class GenericDAOHibernate<T, ID extends Serializable>
		implements GenericDAO<T, ID> {

	@Autowired
	protected SessionFactory sessionFactory;

	public static CacheManager cacheMgr = null;

	public void getCache() {
		if (cacheMgr == null) {
			// We could use an environment or a VM variable
			cacheMgr = CacheManager.create();
		}

		/*
		 * TODO Cache einbauen.
		Ehcache cache = null;
		if (cacheMgr != null) {
			// cache = cacheMgr.addCacheIfAbsent(name);
			cache = cacheMgr.getEhcache(cacheName);
		}*/
	}

	public void save(T entity) {
		Session hibernateSession = sessionFactory.getCurrentSession();
		hibernateSession.saveOrUpdate(entity);
	}

	public void merge(T entity) {
		Session hibernateSession = sessionFactory.getCurrentSession();
		hibernateSession.merge(entity);
	}

	public void delete(T entity) {
		Session hibernateSession = sessionFactory.getCurrentSession();
		hibernateSession.delete(entity);
	}

	public List<T> findMany(Query query) {
		@SuppressWarnings("unchecked")
		List<T> t = (List<T>) query.list();
		return t;
	}

	public T findOne(Query query) {
		@SuppressWarnings("unchecked")
		T t = (T) query.uniqueResult();
		return t;
	}

	@SuppressWarnings("rawtypes")
	public T findByID(Class clazz, ID id) {
		Session hibernateSession = sessionFactory.getCurrentSession();
		@SuppressWarnings("unchecked")
		T t = (T) hibernateSession.get(clazz, id);
		return t;
	}

	@SuppressWarnings("rawtypes")
	public List<T> findAll(Class clazz) {
		Session hibernateSession = sessionFactory.getCurrentSession();
		Query query = hibernateSession.createQuery("from " + clazz.getName());
		@SuppressWarnings("unchecked")
		List<T> T = query.list();
		return T;
	}
}
