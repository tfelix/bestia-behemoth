package net.bestia.messages;

import org.junit.Assert;
import org.junit.Test;

public class ZoneWrapperMessageTest {
	
	private static final String MESSAGE_TEST_ID = "message.id";

	@Test
	public void ctor_null_ok() {
		new ZoneWrapperMessage<PingMessage>(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void getWrappedMessageId_null_exception() {
		ZoneWrapperMessage.getWrappedMessageId(null);
	}
	
	@Test
	public void getWrappedMessageId_string_ok() {
		final String id = ZoneWrapperMessage.getWrappedMessageId(MESSAGE_TEST_ID);
		Assert.assertNotNull(id);
	}
	
}
