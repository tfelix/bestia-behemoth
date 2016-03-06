package net.bestia.messages;

import org.junit.Assert;
import org.junit.Test;

public class ZoneWrapperMessageTest {
	
	private static final String MESSAGE_TEST_ID = "message.id";

	@Test(expected=IllegalArgumentException.class)
	public void ctor_null_ok() {
		new ZoneMessageDecorator<PingMessage>(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getWrappedMessageId_null_exception() {
		ZoneMessageDecorator.getWrappedMessageId(null);
	}
	
	@Test
	public void getWrappedMessageId_string_ok() {
		final String id = ZoneMessageDecorator.getWrappedMessageId(MESSAGE_TEST_ID);
		Assert.assertNotNull(id);
	}
	
}
