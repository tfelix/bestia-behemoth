package net.bestia.core.command;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.core.BestiaZoneserverTest;
import net.bestia.core.message.Message;
import net.bestia.core.message.PingMessage;
import net.bestia.core.message.PongMessage;
import net.bestia.core.message.ServerInfoMessage;

public class CommandIntegrationTest extends BestiaZoneserverTest {

	private final static int ACC_ID = 1337;
	
	@Test
	public void ping_cmd_test() {
		Message msg = new PingMessage();
		msg.setAccountId(ACC_ID);

		zone.handleMessage(msg);

		Assert.assertEquals(1, connection.buffer.size());		
		Message recv = connection.buffer.get(0);
		Assert.assertTrue(recv instanceof PongMessage);
		Assert.assertEquals(ACC_ID, recv.getAccountId());
	}
	
	@Test
	public void info_cmd_test() {
		Message msg = new ServerInfoMessage();
		msg.setAccountId(ACC_ID);

		zone.handleMessage(msg);

		Assert.assertEquals(1, connection.buffer.size());		
		Message temp = connection.buffer.get(0);
		Assert.assertTrue(temp instanceof ServerInfoMessage);
		Assert.assertEquals(ServerInfoMessage.MESSAGE_ID, temp.getMessageId());
		ServerInfoMessage recv = (ServerInfoMessage)temp;
		
		//Assert.assertEquals(ACC_ID, recv.);
		//Assert.assertEquals(ACC_ID, recv.getAccountId());
	}
	
}
