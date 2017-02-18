package net.bestia.server;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;

import akka.actor.Address;

/**
 * The {@link DiscoveryService} can be used to extract the necessary
 * cluster information from a Hazelcast instance and to use this information to
 * setup the akka cluster.
 * <p>It serves as a simple service discovery</p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class DiscoveryService {

	private static final int NUM_SEED_NODES = 3;
	private final IList<Address> clusterAdress;

	@Autowired
	public DiscoveryService(HazelcastInstance hcInstance) {

		this.clusterAdress = hcInstance.getList("cluster_nodes");
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

		return clusterAdress.size() < NUM_SEED_NODES;
	}

	/**
	 * Retrives the first currently active 3 seed nodes.
	 * 
	 * @return The list of current active seed nodes.
	 */
	public List<Address> getClusterSeedNodes() {
		return clusterAdress.stream().limit(3).collect(Collectors.toList());
	}

	/**
	 * Adds a new cluster node the registry.
	 * 
	 * @param address
	 *            The node to be added.
	 */
	public void addClusterNode(Address address) {

		clusterAdress.add(address);
	}

	/**
	 * Removes a cluster node from the registry.
	 * 
	 * @param address
	 *            The address to be removed.
	 */
	public void removeClusterNode(Address address) {

		clusterAdress.remove(address);
	}

}
