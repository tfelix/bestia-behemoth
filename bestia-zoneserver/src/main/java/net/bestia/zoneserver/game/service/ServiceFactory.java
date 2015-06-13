package net.bestia.zoneserver.game.service;

import net.bestia.model.service.AccountServiceManager;


public interface ServiceFactory {

	public AccountServiceManager getAccountServiceFactory();

	public BestiaServiceFactory getBestiaServiceFactory();
}