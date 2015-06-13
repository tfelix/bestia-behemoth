package net.bestia.model.service;

import net.bestia.model.DAOLocator;
import net.bestia.model.dao.AccountDAO;

/**
 * Use this factory to get service implementations of all the data objects and
 * to interact with them. This service implementation will keep track of all
 * changes and updates (generate the appropriate messages and send them over the
 * server interface to the client).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public final class HibernateServiceFactory implements ServiceFactory {

	private final DAOLocator locator;
	private final MessageSender sender;

	public HibernateServiceFactory(MessageSender sender) {
		if(sender == null) {
			throw new IllegalArgumentException("MessageSender can not be null.");
		}
		
		this.locator = new DAOLocator();
		this.sender = sender;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.core.game.service.ServiceFactory#getBestiaService()
	 */
	@Override
	public AccountServiceManager getAccountServiceFactory() {
		AccountDAO accDAO = locator.getDAO(AccountDAO.class);
		final AccountServiceManager accServiceFactory = new AccountServiceManager(accDAO, sender);
		return accServiceFactory;
	}
}
