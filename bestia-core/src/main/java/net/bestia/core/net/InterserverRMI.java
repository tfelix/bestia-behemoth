package net.bestia.core.net;

import java.net.URL;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface InterserverRMI extends Remote {

	public ServerInfo getServerForConnection(int accountId)
			throws RemoteException;

	/**
	 * Returns information about all currently registered zoneserver.
	 * 
	 * @return List of information about all currently registerd zoneservers.
	 * @throws RemoteException
	 */
	public List<ServerInfo> getAllServer() throws RemoteException;

	/**
	 * If a new zone wants to join the bestia server network it must register
	 * itself with the interserver.
	 * <p/>
	 * After a successful join the interserver will then invalidate all
	 * zoneserver caches on the registered zones in order to make the new zone
	 * public.
	 * 
	 * @throws IllegalStateException
	 *             If the server info is not correct. A single zone can only
	 *             responsible for a given map. If there are illegal
	 *             configurations the interserver will deny registering this
	 *             zone.
	 */
	public void registerServer(ServerInfo info) throws RemoteException,
			IllegalStateException;
}
