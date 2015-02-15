package net.bestia.core.game.service;

import net.bestia.core.net.Messenger;
import net.bestia.core.persist.AccountDAO;

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
	private final Messenger messenger;

	public HibernateServiceFactory(Messenger messenger) {
		ctx = new ClassPathXmlApplicationContext("spring-config.xml");
		
		this.messenger = messenger;
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
			accServiceFactory = new AccountServiceFactory(accDAO, messenger);
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
