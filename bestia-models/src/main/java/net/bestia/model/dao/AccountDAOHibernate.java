package net.bestia.model.dao;


import net.bestia.model.Account;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;

@Repository("accountDao")
public class AccountDAOHibernate extends GenericDAOHibernate<Account, Long> implements AccountDAO {

	@Override
	public Account findByEmail(String email) {
		Query query = currentSession().createQuery("from Account a where a.email = :email");
		query.setParameter("email", email);
		return (Account) query.uniqueResult();
	}
}