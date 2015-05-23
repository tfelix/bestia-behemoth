package net.bestia.core.persist;

import net.bestia.model.Account;

public interface AccountDAO extends GenericDAO<Account, Integer>{

	public abstract Account getByIdentifier(String name);

}