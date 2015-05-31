package net.bestia.interserver;

public interface InterserverSubscriber {

	public abstract void connect();

	public abstract void disconnect();

	public abstract void subscribe(String topic);

	public abstract void unsubscribe(String topic);

}