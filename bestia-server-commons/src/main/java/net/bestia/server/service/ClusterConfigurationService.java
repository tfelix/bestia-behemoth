package net.bestia.server.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import akka.actor.Address;

/**
 * The {@link ClusterConfigurationService} can be used to extract the necessairy cluster
 * information from a hazelcast instance and to use this information to setup
 * the akka cluster.
 * 
 * @author tbf
 *
 */
@Service
public class ClusterConfigurationService {

	private static final Logger LOG = LoggerFactory.getLogger(ClusterConfigurationService.class);

	private static final int NUM_SEED_NODES = 3;

	private static final String MAP_NAME = "cluster_config";

	private static final String CURRENT_NUM_SEED_NODES = "current_num_seed_node";
	private static final String CLUSTER_NODES = "cluster_nodes";

	private final IMap<String, Object> data;

	@Autowired
	public ClusterConfigurationService(HazelcastInstance hcInstance) {
		
		this.data = hcInstance.getMap(MAP_NAME);
	}

	/**
	 * Checks against the database if we should join the cluster as a seed node.
	 * This is the case if the desired number of {@link #NUM_SEED_NODES} has not
	 * yet been reached.
	 * 
	 * The method call is thread safe against a lock. So concurrent access is
	 * save.
	 * 
	 * @return TRUE if the current node should join as seed. FALSE otherwise.
	 */
	public boolean shoudlJoinAsSeedNode() {
		data.lock(CURRENT_NUM_SEED_NODES);
		boolean shouldRun;
		try {
			Integer value = (Integer) data.get(CURRENT_NUM_SEED_NODES);
			
			if(value == null) {
				value = 0;
			}
			
			shouldRun = value < NUM_SEED_NODES;
			
			if(shouldRun) {
				final Integer newValue = value + 1;
				data.set(CURRENT_NUM_SEED_NODES, newValue);
			}
		} finally {
			data.unlock(CURRENT_NUM_SEED_NODES);
		}
		
		return shouldRun;
	}

	/**
	 * Retrives the current active seed nodes.
	 * 
	 * @return The list of current active seed nodes.
	 */
	public List<Address> getClusterNodes() {
		data.lock(CLUSTER_NODES);
		try {
			@SuppressWarnings("unchecked")
			List<Address> nodes = (List<Address>) data.get(CLUSTER_NODES);
			
			if(nodes == null) {
				nodes = new ArrayList<Address>();
				data.set(CLUSTER_NODES, nodes);
			}
			
			return nodes;
		} finally {
			data.unlock(CLUSTER_NODES);
		}
	}
	
	public void addClusterNode(Address address) {
		data.lock(CLUSTER_NODES);
		try {
			@SuppressWarnings("unchecked")
			List<Address> nodes = (List<Address>) data.get(CLUSTER_NODES);
			if(nodes == null) {
				nodes = new ArrayList<Address>();
			}
			nodes.add(address);
			data.set(CLUSTER_NODES, nodes);
		} finally {
			data.unlock(CLUSTER_NODES);
		}
	}
	
	public void removeClusterNode(Address address) {
		
	}

	public void disconnectSeedNode() {
		data.lock(CURRENT_NUM_SEED_NODES);
		try {
			final Integer value = (Integer) data.get(CURRENT_NUM_SEED_NODES);
			Integer newValue = value - 1;

			// Should not happen if our locking works properly but u never
			// know...
			if (newValue < 0) {
				LOG.warn("Disconnected more seed notes then existed.");
				newValue = 0;
			}

			data.set(CURRENT_NUM_SEED_NODES, newValue);
		} finally {
			data.unlock(CURRENT_NUM_SEED_NODES);
		}
	}

}