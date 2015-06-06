package net.bestia.zoneserver.game.service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.zoneserver.Zoneserver;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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

	private AccountServiceFactory accServiceFactory;
	private BestiaServiceFactory bestiaServiceFactory;
	private ApplicationContext ctx;
	private final Zoneserver server;

	public HibernateServiceFactory(Zoneserver server) {
		if(server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}
		
		this.ctx = new ClassPathXmlApplicationContext("spring-config.xml");		
		this.server = server;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.core.game.service.ServiceFactory#getBestiaService()
	 */
	@Override
	public AccountServiceFactory getAccountServiceFactory() {
		if(accServiceFactory == null) {
			AccountDAO accDAO = (AccountDAO) ctx.getBean("accountDAOHibernate");
			accServiceFactory = new AccountServiceFactory(accDAO, server);
		}
		return accServiceFactory;
	}

	@Override
	public BestiaServiceFactory getBestiaServiceFactory() {
		/*if(bestiaServiceFactory == null) {
			BestiaDAO bestiaDAO = (BestiaDAO) ctx.getBean("bestiaDAOHibernate");
			
		}*/
		return null;
	}
}
