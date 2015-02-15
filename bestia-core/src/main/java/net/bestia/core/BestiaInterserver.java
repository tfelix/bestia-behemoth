package net.bestia.core;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.core.net.InterserverRMI;
import net.bestia.core.net.ServerInfo;

public class BestiaInterserver extends UnicastRemoteObject implements
		InterserverRMI {

	private static final long serialVersionUID = 2244162977964759870L;
	private static final String serverURL = "interserver";
	private static final Logger log = LogManager.getLogger(BestiaInterserver.class);

	public BestiaInterserver() throws RemoteException {

		super();

	}

	@Override
	public ServerInfo getServerForConnection(int accountId)
			throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<ServerInfo> getAllServer() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerServer(ServerInfo info) throws RemoteException,
			IllegalStateException {
		// TODO Auto-generated method stub

	}

	public void start() throws RemoteException {
		try {
			Naming.rebind(serverURL, this);
		} catch (MalformedURLException e) {
			// no op. Should not happen.
		}
	}

	public void stop() {
		try {
			Naming.unbind(serverURL);
			UnicastRemoteObject.unexportObject(this, true);
		} catch(Exception e) {
			log.error("Interserver error during shutdown.", e);
			return;
		}

		log.info("Interserver shut down gracefully.");
	}

}
