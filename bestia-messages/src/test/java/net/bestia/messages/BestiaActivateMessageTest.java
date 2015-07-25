package net.bestia.messages;

import org.junit.Assert;
import org.junit.Test;

public class BestiaActivateMessageTest {
	
	private static final int BESTIA_ID = 5;

	@Test
	public void general_test() {
		BestiaActivateMessage msg = new BestiaActivateMessage();
		msg.setActivatePlayerBestiaId(BESTIA_ID);
		Assert.assertEquals(BESTIA_ID, msg.getActivatePlayerBestiaId());
	}
	
	@Test
	public void copy_test() {
		BestiaActivateMessage msg1 = new BestiaActivateMessage();
		msg1.setActivatePlayerBestiaId(BESTIA_ID);
		
		BestiaActivateMessage msg2 = new BestiaActivateMessage(msg1);
		
		Assert.assertEquals(BESTIA_ID, msg2.getActivatePlayerBestiaId());
	}
}
