package net.bestia.core.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.processing.Messager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;

import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.message.Message;

/**
 * This class is responsible for sending messages asynchronisly. It will detect
 * if we have a local connection to the client if so use the local connection
 * otherwise it will ask the interserver who holds a connection and issue an RPC
 * call to this server.
 * 
 * TODO DIE KLASSE MUSS THREADSAFE SEIN DA SIE GESHARED WIRD.
 * 
 * @author Thomas
 *
 */
public class Messenger {

	private final static Logger log = LogManager.getLogger(Messager.class);

	private final BestiaConnectionInterface localConnection;
	private final ExecutorService worker;
	private final InterserverRMI interserver = null;
	
	private final List<BestiaConnectionInterfaceRMI> zoneCache = new ArrayList<>();
	private final Map<Integer, BestiaConnectionInterfaceRMI> connectionCache = new HashMap<>();

	public Messenger(BestiaConnectionInterface localConnection,
			ExecutorService worker) {
		if (localConnection == null) {
			throw new IllegalArgumentException(
					"BestiaConnectionInterface can not be null.");
		}

		this.localConnection = localConnection;
		this.worker = worker;
	}

	public void sendMessage(final Message msg) {
		
		log.trace("Sending message: {}", msg.toString());

		// Is it a broadcast message? Then we need to deliver it locally as well
		// as to all other servers.
		if(msg.isBroadcast()) {
			sendBroadcastMsg(msg);
			return;
		}

		// Check if we have a local connection.
		if (localConnection.isConnected(msg.getAccountId())) {

			worker.execute(new Runnable() {
				@Override
				public void run() {
					try {
						localConnection.sendMessage(msg);
					} catch (IOException e) {
						log.error("Local message {} could not be delivered.",
								msg.toString());
					}
				}
			});

			return;
		}

		// At the moment we have only local connections.
		/*
		// First try to deliver the message via our local caching.
		BestiaConnectionInterfaceRMI rmiConnection = getCachedConnection();
		rmiConnection.sendMessage(msg);

		// if this fails we have to ask the interserver where the user is connected.
		interserver.getServerForConnection(msg.getAccountId());*/

	}

	private BestiaConnectionInterfaceRMI getCachedConnection() {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	 * Helper to send a message as broadcast to all registered zones.
	 * 
	 * @param msg
	 */
	private void sendBroadcastMsg(final Message msg) {
		worker.execute(new Runnable() {
			
			@Override
			public void run() {
				try {
					localConnection.sendMessage(msg);
				} catch (IOException e) {
					log.error("Local message {} could not be delivered.",
							msg.toString());
				}		
			}
		});
		
		for (final BestiaConnectionInterfaceRMI zone : zoneCache) {
			worker.execute(new Runnable() {
				
				@Override
				public void run() {
					try {
						zone.sendMessage(msg);
					} catch (IOException e) {
						log.error("RMI message {} could not be delivered. Reason: {}",
								msg.toString(), e.getMessage());
					}
				}
			});
		}
	}
}
