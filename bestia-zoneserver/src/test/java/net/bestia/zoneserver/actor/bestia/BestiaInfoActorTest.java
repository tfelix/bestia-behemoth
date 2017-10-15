package net.bestia.zoneserver.actor.bestia;

import java.util.HashSet;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.StatusService;
import net.bestia.messages.bestia.BestiaInfoRequestMessage;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.zoneserver.actor.ActorTestConfig;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.service.PlayerEntityService;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(ActorTestConfig.class)
public class BestiaInfoActorTest {

	private static ActorSystem system;

	private final static long ACC_ID = 1;

	private static final long ENTITY_ID = 2;

	@Autowired
	private ApplicationContext appCtx;

	@MockBean
	private EntityService entityService;

	@MockBean
	private PlayerEntityService playerEntityService;

	@MockBean
	private PlayerBestiaDAO playerBestiaDao;
	
	@MockBean
	private StatusService statusService;
	
	@Mock
	private Entity entity;

	// Can not be a mock. Need real message for selecting in actor.
	private BestiaInfoRequestMessage infoReq = new BestiaInfoRequestMessage(ACC_ID);

	@BeforeClass
	public static void initialize() {
		system = ActorSystem.create();
	}

	@AfterClass
	public static void teardown() {
		TestKit.shutdownActorSystem(system);
		system = null;
	}

	@Before
	public void setup() {
		SpringExtension.initialize(system, appCtx);
		
		Set<Entity> bestias = new HashSet<>();
		bestias.add(entity);

		Mockito.when(playerEntityService.getPlayerEntities(ACC_ID)).thenReturn(bestias);
		
		Mockito.when(entity.getId()).thenReturn(ENTITY_ID);
	}

	@Test
	public void onBestiaInfoRequest_sends_info_to_clients() {
		new TestKit(system) {
			{
				final ActorRef actor = SpringExtension.unnamedActorOf(system, BestiaInfoActor.class);

				within(duration("1 seconds"), () -> {
					actor.tell(infoReq, getRef());
					expectNoMsg();
					return null;
				});

				Mockito.verify(playerEntityService).getPlayerEntities(ACC_ID);
			}
		};
	}
}
