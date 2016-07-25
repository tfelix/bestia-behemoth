package de.bestia.next.zoneserver;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import de.bestia.next.zoneserver.component.ClusterConfig;

public class Zoneserver {
	private static final Logger LOG = LoggerFactory.getLogger(Zoneserver.class);

	private static final String ACTOR_SYSTEM_NAME = "BestiaZoneSystem";

	private ActorSystem system;
	private HazelcastInstance hazelcastInstance;
	private ClusterConfig config;

	public void run() {
		// Create an Akka system
		system = ActorSystem.create(ACTOR_SYSTEM_NAME);

		hazelcastInstance = Hazelcast.newHazelcastInstance();

		config = new ClusterConfig(hazelcastInstance);

		final Address myAddress = Cluster.get(system).selfAddress();
		LOG.info("Zoneserver Akka Address: {}", myAddress);
		
		joinCluster();
		
		config.addClusterNode(myAddress);
		//System.out.println(config.getClusterNodes().toString());
	}

	private void joinCluster() {
		Set<Address> clusterNodes = config.getClusterNodes();
		
		if(clusterNodes.size() > 0) {
			final Address addr = config.getClusterNodes().iterator().next();
			System.out.println("Joining: " + addr);
			Cluster.get(system).join(addr);
		} 
		
		//config.addClusterNode(address);
		
		
	}

}
