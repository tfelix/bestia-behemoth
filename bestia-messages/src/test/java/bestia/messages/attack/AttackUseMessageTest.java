package bestia.messages.attack;

import org.junit.Assert;
import org.junit.Test;

public class AttackUseMessageTest {
	
	@Test
	public void ctor_works() {
		AttackUseMessage msg = new AttackUseMessage(1);
		Assert.assertEquals(1, msg.getAccountId());
	}

}
