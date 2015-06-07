package net.bestia.zoneserver.game.service;


public interface ServiceFactory {

	public AccountServiceManager getAccountServiceFactory();

	public BestiaServiceFactory getBestiaServiceFactory();
}