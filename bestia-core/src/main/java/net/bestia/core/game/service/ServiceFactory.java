package net.bestia.core.game.service;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.message.Message;

public interface ServiceFactory {

	public AccountServiceFactory getAccountServiceFactory();

	public BestiaService getBestiaService();
	
	
	/**
	 * Used to set the internal message queue of the ServiceFactory.
	 * Must be done because the concrete factory is created outside of the
	 * server and the queue is later instanced inside of the server.
	 * @param queue
	 */
	public void setMessageQueue(BlockingQueue<Message> queue);

}