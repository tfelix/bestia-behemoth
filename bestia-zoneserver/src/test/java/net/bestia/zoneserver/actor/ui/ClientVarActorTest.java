package net.bestia.zoneserver.actor.ui;

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
import net.bestia.messages.ui.ClientVarRequestMessage;
import net.bestia.model.domain.ClientVar;
import net.bestia.zoneserver.actor.ActorTestConfig;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.service.ClientVarService;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(ActorTestConfig.class)
public class ClientVarActorTest {

	private static ActorSystem system;

	@Autowired
	private ApplicationContext appCtx;

	private static final long ACC_ID = 1;
	private static final long WRONG_ACC_ID = 2;
	private static final String KEY = "test";
	private static final String UUID = "test-1235-124545-122345";
	private static final String DATA = "myData";

	@MockBean
	private ClientVarService cvarService;

	@Mock
	private ClientVar cvar;

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

		Mockito.when(cvar.getData()).thenReturn(DATA);
		Mockito.when(cvar.getDataLength()).thenReturn(DATA.length());
		Mockito.when(cvar.getKey()).thenReturn(KEY);

		Mockito.when(cvarService.find(ACC_ID, KEY)).thenReturn(cvar);
		Mockito.when(cvarService.find(WRONG_ACC_ID, KEY)).thenReturn(null);
	}

	@Test
	public void onRequest_answersWithCvar() {
		new TestKit(system) {
			{
				TestKit sender = new TestKit(system);
				ActorRef cvarActor = SpringExtension.actorOf(system, ClientVarActor.class, "reqReq");

				ClientVarRequestMessage msg = ClientVarRequestMessage.request(ACC_ID, KEY, UUID);
				cvarActor.tell(msg, sender.getRef());

				expectMsg(duration("1 second"), ClientVarRequestMessage.class);
			}
		};

		// Check the setting.
		Mockito.verify(cvarService).find(ACC_ID, KEY);
	}

	@Test
	public void onDelete_deleteCvar() {
		TestKit sender = new TestKit(system);
		ActorRef cvarActor = SpringExtension.actorOf(system, ClientVarActor.class, "delReq");

		ClientVarRequestMessage msg = ClientVarRequestMessage.delete(ACC_ID, KEY);
		cvarActor.tell(msg, sender.getRef());

		// Check the setting.
		Mockito.verify(cvarService).delete(ACC_ID, KEY);
	}

	@Test
	public void onSet_setsCvar() {
		new TestKit(system) {
			{
				TestKit sender = new TestKit(system);
				ActorRef cvarActor = SpringExtension.actorOf(system, ClientVarActor.class, "setReq");
				ClientVarRequestMessage msg = ClientVarRequestMessage.set(ACC_ID, KEY, UUID, DATA);
				cvarActor.tell(msg, sender.getRef());

				// Check the setting.
				Mockito.verify(cvarService).set(ACC_ID, KEY, DATA);
			}
		};
	}

	@Test
	public void onRequest_notOwnerOfCvar_noAnswer() {
		new TestKit(system) {
			{
				TestKit sender = new TestKit(system);
				ActorRef cvarActor = SpringExtension.actorOf(system, ClientVarActor.class, "onReqNotOwn");

				ClientVarRequestMessage msg = ClientVarRequestMessage.request(WRONG_ACC_ID, KEY, UUID);
				cvarActor.tell(msg, sender.getRef());

				expectNoMsg(duration("1 second"));
				
				//Mockito.verify(cvarService).find(WRONG_ACC_ID, KEY);
			}
		};

		// Check the setting.
		//Mockito.verify(cvarService).find(WRONG_ACC_ID, KEY);
	}
}
