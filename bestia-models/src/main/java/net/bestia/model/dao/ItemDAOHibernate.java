package net.bestia.model.dao;

import net.bestia.model.domain.Item;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;

@Repository("itemDao")
public class ItemDAOHibernate extends GenericDAOHibernate<Item, Integer> implements ItemDAO {

	@Override
	public Item findItemByName(String itemDbName) {
		Query query = currentSession().createQuery("from Item i where i.itemDbName = :dbName");
		query.setParameter("dbName", itemDbName);
		query.setMaxResults(1);
		return (Item) query.list().get(0);
	}
}
