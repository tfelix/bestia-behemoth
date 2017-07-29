package net.bestia.zoneserver.service;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * This services helps to keep track of the client latency. It generates
 * timestamps and manages the latency calculation. This can and should be used
 * in order to reduce lag while sending position and motion updates to the
 * clients.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class LatencyService {

	private static final Logger LOG = LoggerFactory.getLogger(LatencyService.class);

	private static final int MAX_NUM_LATENCY_DATA = 20;

	private static final String LATENCY_STORE = "latency.store";
	private static final String STAMP_STORE = "latency.stamp";

	private final IMap<Long, Queue<Integer>> latencyStore;
	private final IMap<Long, Long> timestampStore;

	/**
	 * Ctor.
	 * 
	 * @param hz
	 *            Instance of a hazelcast server.
	 */
	public LatencyService(HazelcastInstance hz) {

		latencyStore = hz.getMap(LATENCY_STORE);
		timestampStore = hz.getMap(STAMP_STORE);
	}

	/**
	 * Generates a new timestamp for the given account id.
	 * 
	 * @param accountId
	 * @return
	 */
	public long getTimestamp(long accountId) {
		final long stamp = System.currentTimeMillis();
		timestampStore.put(accountId, stamp);
		return stamp;
	}
	
	/**
	 * Generates a new latency entry for the given client. It throws if no
	 * server timestamp was previously set via {@link #getTimestamp(long)}. The
	 * server stamp is used as precauson because the server timestamp could have
	 * been changed in the meantime. Only if it matches the saved value the
	 * latency estimation will be added.
	 * 
	 * @param accountId
	 *            The account id.
	 * @param clientStamp
	 *            The timestamp sent via the client.
	 */
	public void addLatency(long accountId, long delta) {
		
		if(delta < 0) {
			return;
		}

	}

	/**
	 * Generates a new latency entry for the given client. It throws if no
	 * server timestamp was previously set via {@link #getTimestamp(long)}. The
	 * server stamp is used as precauson because the server timestamp could have
	 * been changed in the meantime. Only if it matches the saved value the
	 * latency estimation will be added.
	 * 
	 * @param accountId
	 *            The account id.
	 * @param clientStamp
	 *            The timestamp sent via the client.
	 */
	public void addLatency(long accountId, long clientStamp, long serverStamp) {

		final long savedStamp = timestampStore.getOrDefault(accountId, 0L);

		if (savedStamp == 0) {
			throw new IllegalStateException("Set a server stamp with getTimestamp first.");
		}

		if (serverStamp != savedStamp) {
			LOG.warn("Server and client timestamps do not match. Not processing.");
			return;
		}

		if (clientStamp < serverStamp) {
			throw new IllegalArgumentException("Client stamp is smaller then server stamp.");
		}

		final int latency = (int) (clientStamp - serverStamp);

		Queue<Integer> data = latencyStore.get(accountId);

		if (data == null) {
			data = new LinkedList<>();
		}

		if (data.size() == MAX_NUM_LATENCY_DATA) {
			data.poll();
		}

		data.add(latency);
		latencyStore.put(accountId, data);
		LOG.debug("Added latency {} ms for user {}.", latency, accountId);
	}

	/**
	 * Returns the client latency. It throws if there is no entry found for the
	 * given account id.
	 * 
	 * @param accountId
	 *            The account to return the lataency for.
	 * @return The estimated latency to this client.
	 */
	public int getClientLatency(long accountId) {

		Queue<Integer> stamps = latencyStore.get(accountId);

		if (stamps == null) {
			throw new IllegalStateException("No latency for account " + accountId + " found");
		}

		Integer[] data = new Integer[stamps.size()];
		data = stamps.toArray(data);
		Arrays.sort(data);

		int median = 0;

		if (data.length % 2 == 0) {
			median = (data[data.length / 2] + data[data.length / 2 - 1]) / 2;
		} else {
			median = data[data.length / 2];
		}

		LOG.debug("Found median latency {} ms for user {}.", median, accountId);

		return median;
	}

}
