package bestia.messages.attack;

import net.bestia.messages.attack.AttackListRequestMessage;
import org.junit.Assert;
import org.junit.Test;


public class AttackListRequestMessageTest {
	
	@Test
	public void ctor_works() {
		AttackListRequestMessage msg = new AttackListRequestMessage(1);
		Assert.assertEquals(1, msg.getAccountId());
	}

}
