package net.bestia.zoneserver.actor.rest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import net.bestia.messages.web.AccountLoginRequest;
import net.bestia.zoneserver.actor.ActorTestConfig;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.service.LoginService;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(ActorTestConfig.class)
public class RequestLoginActorTest {

	@MockBean
	private LoginService loginService;

	@Autowired
	private ActorSystem system;

	private AccountLoginRequest validReq;
	private AccountLoginRequest invalidReq;

	private final static long ACC_ID = 1;
	private final static String USERNAME = "blubber";
	private final static String TOKEN ="12345-12345-12455-12455";

	@Before
	public void setup() {
		
		validReq = new AccountLoginRequest(USERNAME, "blubber");
		invalidReq = new AccountLoginRequest(USERNAME, "wrong_password");
		
		
		Mockito.when(loginService.setNewLoginToken(validReq)).thenAnswer(new Answer<AccountLoginRequest>() {
			@Override
			public AccountLoginRequest answer(InvocationOnMock invocation) throws Throwable {
				return validReq.success(ACC_ID, TOKEN);
			}
		});
		
		Mockito.when(loginService.setNewLoginToken(invalidReq)).thenAnswer(new Answer<AccountLoginRequest>() {
			@Override
			public AccountLoginRequest answer(InvocationOnMock invocation) throws Throwable {
				return validReq.fail();
			}
		});
	}

	@Test
	public void AccountLoginRequest_validRequest_loginOk() {
		TestKit sender = new TestKit(system);
		ActorRef router = SpringExtension.actorOf(system, RequestLoginActor.class, "validRequest");

		router.tell(validReq, sender.getRef());

		AccountLoginRequest msg = sender.expectMsgClass(AccountLoginRequest.class);
		Assert.assertEquals(ACC_ID, msg.getAccountId());
		Assert.assertFalse(msg.getToken().isEmpty());
		Assert.assertTrue(msg.getPassword().isEmpty());
	}

	@Test
	public void AccountLoginRequest_invalidRequest_loginNotOk() {
		TestKit sender = new TestKit(system);
		ActorRef router = SpringExtension.actorOf(system, RequestLoginActor.class, "invalidRequest");

		router.tell(invalidReq, sender.getRef());

		AccountLoginRequest msg = sender.expectMsgClass(AccountLoginRequest.class);
		Assert.assertEquals(0, msg.getAccountId());
		Assert.assertTrue(msg.getToken().isEmpty());
		Assert.assertTrue(msg.getPassword().isEmpty());
		Assert.assertEquals(USERNAME, msg.getUsername());
	}

}
