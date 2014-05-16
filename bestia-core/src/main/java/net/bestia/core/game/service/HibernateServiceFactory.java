package net.bestia.core.game.service;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.message.Message;
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
	private BlockingQueue<Message> messageQueue;
	private ApplicationContext ctx;

	public HibernateServiceFactory() {
		ctx = new ClassPathXmlApplicationContext("spring-config.xml");
	}

	/*
	 * private
	 * 
	 * 
	 * 
	 * /* (non-Javadoc)
	 * 
	 * @see net.bestia.core.game.service.ServiceFactory2#getBestiaService()
	 */
	@Override
	public BestiaService getBestiaService() {
		return null;
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
			accServiceFactory = new AccountServiceFactory(accDAO, messageQueue);
		}
		return accServiceFactory;
	}

	@Override
	public void setMessageQueue(BlockingQueue<Message> queue) {
		this.messageQueue = queue;		
	}
}
