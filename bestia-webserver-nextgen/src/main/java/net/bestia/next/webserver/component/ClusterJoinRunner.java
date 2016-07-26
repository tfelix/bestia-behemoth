package net.bestia.next.webserver.component;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import net.bestia.next.service.ClusterConfig;

/**
 * This class will attempt to join the akka cluster of the zones.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ClusterJoinRunner implements CommandLineRunner {
	
	private final static Logger LOG = LoggerFactory.getLogger(ClusterJoinRunner.class);
	
	@Autowired
	private ActorSystem system;
	
	@Autowired
	private HazelcastInstance hazelClient;
	
	private ClusterConfig clusterConfig;

	@Override
	public void run(String... arg0) throws Exception {

		clusterConfig = new ClusterConfig(hazelClient);
		
		final List<Address> seedNodes = clusterConfig.getClusterNodes();
		
		LOG.info("Attempting to joing the bestia cluster...");
		
		Cluster.get(system).joinSeedNodes(seedNodes);
	}

}
