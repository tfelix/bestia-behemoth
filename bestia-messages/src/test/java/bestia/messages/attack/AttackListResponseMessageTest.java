package bestia.messages.attack;

import net.bestia.messages.attack.AttackListResponseMessage;
import net.bestia.model.domain.BestiaAttack;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AttackListResponseMessageTest {
	
	@Test
	public void ctor_works() {
		AttackListResponseMessage msg = new AttackListResponseMessage(1);
		Assert.assertEquals(1, msg.getAccountId());
	}
	
	@Test(expected=NullPointerException.class)
	public void setAttacks_null_throws() {
		AttackListResponseMessage msg = new AttackListResponseMessage(1);
		msg.setAttacks(null);
	}
	
	@Test
	public void setAttacks_works() {
		AttackListResponseMessage msg = new AttackListResponseMessage(1);
		List<BestiaAttack> atks = new ArrayList<>();
		atks.add(new BestiaAttack());
		msg.setAttacks(atks);
	}
}
