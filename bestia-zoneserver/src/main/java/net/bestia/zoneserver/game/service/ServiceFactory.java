package net.bestia.zoneserver.game.service;


public interface ServiceFactory {

	public AccountServiceFactory getAccountServiceFactory();

	public BestiaServiceFactory getBestiaServiceFactory();
}