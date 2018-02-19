package bestia.memoryserver.configuration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.hazelcast.core.HazelcastInstance;

import bestia.memoryserver.persistance.ComponentMapStore;
import bestia.memoryserver.persistance.EntityMapStore;

@RunWith(MockitoJUnitRunner.class)
public class HazelcastConfigurationTest {
	
	private HazelcastConfiguration config;
	
	@Mock
	private EntityMapStore entityStore;
	
	@Mock
	private ComponentMapStore compStore;
	
	@Before
	public void setup() {
		
		config = new HazelcastConfiguration();
	}
	
	@Test
	public void hazelcastInstance_returnsInstance() {
		HazelcastInstance ins = config.hazelcastInstance(entityStore, compStore);
		Assert.assertNotNull(ins);
		ins.shutdown();
	}
}
