package net.bestia;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;

public class HazelMock {
	
	public static HazelcastInstance hazelcastMock() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
		HazelcastInstance hz = hzFact.newHazelcastInstance();
		return hz;
	}

	public static HazelcastInstance hazelcast() {
		Config config = new Config();
		HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);
		return hz;
	}
}