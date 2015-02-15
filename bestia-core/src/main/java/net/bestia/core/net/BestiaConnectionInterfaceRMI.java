package net.bestia.core.net;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.message.Message;

public interface BestiaConnectionInterfaceRMI extends Remote, BestiaConnectionInterface {
	
	@Override
	public void sendMessage(Message message) throws IOException, RemoteException;

}
