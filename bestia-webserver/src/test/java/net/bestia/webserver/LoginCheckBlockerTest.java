package net.bestia.webserver;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import net.bestia.messages.LoginAuthMessage;
import net.bestia.messages.LoginAuthReplyMessage;

public class LoginCheckBlockerTest {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private BestiaConnectionProvider provider;

	@Before
	public void before() {
		provider = Mockito.mock(BestiaConnectionProvider.class);
	}

	@Test
	public void isAuthenticated_falseId_false() throws IOException {
		final LoginCheckBlocker lcb = new LoginCheckBlocker(provider);

		final int accId = 1;
		final String token = "lalalal124";

		// Verify
		final LoginAuthMessage authMsg = new LoginAuthMessage(accId, token);
		final LoginAuthReplyMessage authReply = new LoginAuthReplyMessage(authMsg);
		authReply.setLoginState(LoginAuthReplyMessage.LoginState.DENIED);

		executor.schedule(new Runnable() {

			@Override
			public void run() {
				// Send reply.
				lcb.receivedAuthReplayMessage(authReply);
			}
		}, 500, TimeUnit.MILLISECONDS);

		final boolean result = lcb.isAuthenticated(accId, token);
		Mockito.verify(provider).publishInterserver(authMsg);
		Assert.assertFalse(result);

	}

	@Test
	public void isAuthenticated_trueId_true() throws IOException {
		final LoginCheckBlocker lcb = new LoginCheckBlocker(provider);

		final int accId = 1;
		final String token = "lalalal124";

		// Verify
		// Das hier schlauer machen. Rquest ID wird neu erzeugt.
		final LoginAuthMessage authMsg = new LoginAuthMessage(accId, token);
		final LoginAuthReplyMessage authReply = new LoginAuthReplyMessage(authMsg);
		authReply.setLoginState(LoginAuthReplyMessage.LoginState.AUTHORIZED);

		executor.schedule(new Runnable() {

			@Override
			public void run() {
				// Send reply.
				lcb.receivedAuthReplayMessage(authReply);
			}
		}, 500, TimeUnit.MILLISECONDS);

		final boolean result = lcb.isAuthenticated(accId, token);
		Mockito.verify(provider).publishInterserver(authMsg);
		Assert.assertFalse(result);
	}

	@Test
	public void ctor_null_exception() {
		new LoginCheckBlocker(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void isAuthenticated_nullArgs_exception() {
		final LoginCheckBlocker lcb = new LoginCheckBlocker(provider);
		lcb.isAuthenticated(0, null);
	}
}
