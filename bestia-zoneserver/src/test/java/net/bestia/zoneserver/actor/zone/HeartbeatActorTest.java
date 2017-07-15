package net.bestia.zoneserver.actor.zone;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.annotation.PostConstruct;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.testkit.javadsl.TestKit;
import net.bestia.server.DiscoveryService;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.configuration.StaticConfigService;

@RunWith(SpringRunner.class)
@SpringBootTest
public class HeartbeatActorTest {
	
	private static final String SERVER_NAME = "test124";

	private static ActorSystem system;

	@MockBean
	private StaticConfigService configService;

	@MockBean
	private DiscoveryService discoveryService;
	
	@Autowired
	private ApplicationContext springCtx;

	@Before
	public void setup() {
		
		when(configService.getServerName()).thenReturn(SERVER_NAME);
	}
	
	@BeforeClass
	public static void prepare() {
		system = ActorSystem.create();
	}
	
	@PostConstruct
    public void init() {
		SpringExtension.PROVIDER.get(system).initialize(springCtx);
    }

	@AfterClass
	public static void teardown() {
		TestKit.shutdownActorSystem(system);
		system = null;
	}

	@Test
	public void testIt() {
		/*
		 * Wrap the whole test procedure within a testkit constructor if you
		 * want to receive actor replies or use Within(), etc.
		 */
		new TestKit(system) {
			{
				SpringExtension.actorOf(getSystem(), HeartbeatActor.class);
				
				// the run() method needs to finish within 3 seconds
				String strDuration = String.format("%d seconds", HeartbeatActor.HEARTBEAT_INTERVAL_S + 5);
				within(duration(strDuration), () -> {
					
					expectNoMsg();
					
					verify(discoveryService).addClusterNode(SERVER_NAME, any(Address.class));

					return null;
				});
			}
		};
	}

}
