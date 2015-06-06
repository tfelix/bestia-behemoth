package net.bestia.model.persistence;

import net.bestia.model.Account;

public interface AccountDAO extends GenericDAO<Account, Integer>{

	public abstract Account getByIdentifier(String name);

}