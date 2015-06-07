package net.bestia.zoneserver.game.service;

import net.bestia.model.DAOLocator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.zoneserver.Zoneserver;

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
	private final Zoneserver server;

	public HibernateServiceFactory(Zoneserver server) {
		if(server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}
		
		this.locator = new DAOLocator();
		this.server = server;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.core.game.service.ServiceFactory#getBestiaService()
	 */
	@Override
	public AccountServiceManager getAccountServiceFactory() {
		AccountDAO accDAO = locator.getObject(AccountDAO.class);
		final AccountServiceManager accServiceFactory = new AccountServiceManager(accDAO, server);
		return accServiceFactory;
	}

	@Override
	public BestiaServiceFactory getBestiaServiceFactory() {
		return null;
	}
}
