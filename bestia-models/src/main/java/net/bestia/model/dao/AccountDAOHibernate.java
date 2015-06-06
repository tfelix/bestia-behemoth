package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.Account;

import org.hibernate.Query;
import org.springframework.stereotype.Repository;

@Repository("accountDao")
public class AccountDAOHibernate extends GenericDAOHibernate<Account, Long> implements AccountDAO {
	

	@Override
	public Account find(Long key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Account> list() {
		// TODO Auto-generated method stub
		return null;
	}

}
