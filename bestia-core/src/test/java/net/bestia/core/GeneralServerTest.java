package net.bestia.core;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import net.bestia.core.connection.NullConnectionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GeneralServerTest {
	
	Registry registry = null;

	@Before
	public void setup() {
		
			try {
				registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	}
	
	@Test
	public void ServerStart_test() throws Exception {
		// First start interserver.
		BestiaInterserver interServer = new BestiaInterserver();
		
		interServer.start();
		
		//BestiaZoneserver zoneServer = new BestiaZoneserver(serviceFactory, new NullConnectionManager(), configFile);
		
	}
	
	
}
