package net.bestia.core.persist;

import net.bestia.core.game.model.Account;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

// TODO Das hier müsste man eigentlich noch in ein eigenes Interface kapseln.
@Service
public class AccountDAOHibernate extends GenericDAOHibernate<Account, Integer> implements AccountDAO {
	

	/* (non-Javadoc)
	 * @see net.bestia.core.persist.AccountDAO#getByIdentifier(java.lang.String)
	 */
	@Override
	public Account getByIdentifier(String name) {
		Account acc = null;
		String sql = "SELECT a FROM Account a WHERE a.email = :name";

		Query query = sessionFactory.getCurrentSession().createQuery(sql)
				.setParameter("name", name);
		acc = findOne(query);
		return acc;
	}
}
