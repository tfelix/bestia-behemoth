package net.bestia.core.net;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI interface for Bestia Zone server.
 * 
 * @author Thomas
 *
 */
public interface ZoneserverRMI extends Remote {

	/**
	 * This will be called if a zone went down or if a new zone registers with
	 * the interserver. All local cached zoneserver information must be
	 * invalidated. This is important especially for the messaging delivery
	 * classes since they very likely will cache zoneserver to avoid network
	 * traffic.
	 * 
	 * @throws RemoteException
	 */
	public void invalidateZoneCache() throws RemoteException;
}
