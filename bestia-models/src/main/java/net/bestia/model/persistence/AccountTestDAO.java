package net.bestia.model.persistence;

import java.util.List;

import net.bestia.model.Account;

import org.hibernate.Query;

public class AccountTestDAO implements AccountDAO {

	@Override
	public void save(Account entity) {
		// no op.
	}

	@Override
	public void merge(Account entity) {
		// no op.
	}

	@Override
	public void delete(Account entity) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<Account> findMany(Query query) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Account findOne(Query query) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Account> findAll(Class clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Account findByID(Class clazz, Integer id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Account getByIdentifier(String name) {
		// TODO Auto-generated method stub
		return null;
	}

}
