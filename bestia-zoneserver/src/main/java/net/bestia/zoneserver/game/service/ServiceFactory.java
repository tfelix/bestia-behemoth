package net.bestia.core.game.service;


public interface ServiceFactory {

	public AccountServiceFactory getAccountServiceFactory();

	public BestiaServiceFactory getBestiaServiceFactory();
}