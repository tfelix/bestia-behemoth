package net.bestia.zoneserver.actor.connection;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.zoneserver.actor.ActorTestConfig;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import net.bestia.zoneserver.client.LoginService;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(ActorTestConfig.class)
public class LoginActorTest {

	private static ActorSystem system;

	private final static long ACC_ID = 1;
	private final static String VALID_TOKEN = "12345hfdfdjgh324";
	private final static String INVALID_TOKEN = "238vndsovnj984";

	@Autowired
	private ApplicationContext appCtx;

	@MockBean
	private LoginService loginService;

	// Can not be a mock. Need real message for selecting in actor.
	private LoginAuthMessage validAuthMsg;
	private LoginAuthMessage invalidAuthMsg;

	public LoginActorTest() {
		
		validAuthMsg = new LoginAuthMessage(ACC_ID, VALID_TOKEN);
		invalidAuthMsg = new LoginAuthMessage(ACC_ID, INVALID_TOKEN);
	}

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

		//Mockito.when(authMsg.getAccountId()).thenReturn(ACC_ID);
		//Mockito.when(authMsg.getToken()).thenReturn(VALID_TOKEN);
		Mockito.when(loginService.canLogin(ACC_ID, VALID_TOKEN)).thenReturn(true);
		Mockito.when(loginService.canLogin(Mockito.anyLong(), Mockito.anyString())).thenReturn(false);
	}

	@Test
	public void registers_RedirectMessage_after_start() {
		new TestKit(system) {
			{
				SpringExtension.actorOf(system, LoginAuthActor.class);
				expectMsgClass(duration("3 second"), RedirectMessage.class);
			}
		};
	}

	@Test
	public void onValidLogin_loginAccepted() {
		new TestKit(system) {
			{
				final ActorRef actor = SpringExtension.actorOf(system, LoginAuthActor.class);

				within(duration("1 seconds"), () -> {
					actor.tell(validAuthMsg, getRef());
					expectMsgClass(LoginAuthReplyMessage.class);
					// Will wait for the rest of the 3 seconds
					expectNoMsg();
					return null;
				});

				Mockito.verify(loginService).canLogin(ACC_ID, VALID_TOKEN);
			}
		};
	}
	
	@Test
	public void onInvalidLogin_loginDenied() {
		new TestKit(system) {
			{
				final ActorRef actor = SpringExtension.actorOf(system, LoginAuthActor.class);

				within(duration("1 seconds"), () -> {
					actor.tell(invalidAuthMsg, getRef());

					final LoginAuthReplyMessage authMsg = expectMsgClass(LoginAuthReplyMessage.class);
					
					Assert.assertEquals(LoginState.DENIED, authMsg.getLoginState());

					// Will wait for the rest of the 3 seconds
					expectNoMsg();
					return null;
				});

				Mockito.verify(loginService).canLogin(ACC_ID, INVALID_TOKEN);
			}
		};
	}
}
