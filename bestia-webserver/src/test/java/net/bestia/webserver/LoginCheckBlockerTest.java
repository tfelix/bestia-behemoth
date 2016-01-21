package net.bestia.webserver;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import net.bestia.messages.LoginAuthMessage;
import net.bestia.messages.LoginAuthReplyMessage;
import net.bestia.messages.Message;

public class LoginCheckBlockerTest {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private BestiaConnectionProvider provider;

	@Before
	public void before() {
		provider = Mockito.mock(BestiaConnectionProvider.class);
	}

	@Test
	public void isAuthenticated_falseId_false() throws IOException {
		final ArgumentCaptor<Message> argument = ArgumentCaptor.forClass(Message.class);

		final LoginCheckBlocker lcb = new LoginCheckBlocker(provider);

		final int accId = 1;
		final String token = "lalalal124";

		// Verify
		executor.schedule(new Runnable() {

			@Override
			public void run() {
				// Send reply.
				try {
					Mockito.verify(provider).publishInterserver(argument.capture());

					final LoginAuthMessage authMsg = (LoginAuthMessage) argument.getValue();
					final LoginAuthReplyMessage authReply = new LoginAuthReplyMessage(authMsg);
					authReply.setLoginState(LoginAuthReplyMessage.LoginState.DENIED);

					lcb.receivedAuthReplayMessage(authReply);
				} catch (IOException e) {

				}
			}
		}, 500, TimeUnit.MILLISECONDS);

		final boolean result = lcb.isAuthenticated(accId, token);
		Assert.assertFalse(result);
	}

	@Test
	public void isAuthenticated_trueId_true() throws IOException {
		final ArgumentCaptor<Message> argument = ArgumentCaptor.forClass(Message.class);

		final LoginCheckBlocker lcb = new LoginCheckBlocker(provider);

		final int accId = 1;
		final String token = "lalalal124";

		// Verify
		executor.schedule(new Runnable() {

			@Override
			public void run() {
				// Send reply.
				try {
					Mockito.verify(provider).publishInterserver(argument.capture());

					final LoginAuthMessage authMsg = (LoginAuthMessage) argument.getValue();
					final LoginAuthReplyMessage authReply = new LoginAuthReplyMessage(authMsg);
					authReply.setLoginState(LoginAuthReplyMessage.LoginState.AUTHORIZED);

					lcb.receivedAuthReplayMessage(authReply);
				} catch (IOException e) {

				}
			}
		}, 500, TimeUnit.MILLISECONDS);

		final boolean result = lcb.isAuthenticated(accId, token);
		Assert.assertTrue(result);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_null_exception() {
		new LoginCheckBlocker(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void isAuthenticated_nullArgs_exception() {
		final LoginCheckBlocker lcb = new LoginCheckBlocker(provider);
		lcb.isAuthenticated(0, null);
	}
}
