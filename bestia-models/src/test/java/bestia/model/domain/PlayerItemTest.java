package bestia.model.domain;

import org.junit.Test;

public class PlayerItemTest {

	// TODO Equipment spezifische Tests.
	
	@Test(expected = IllegalArgumentException.class)
	public void negative_amount_test() {
		Item i = new Item();
		Account a = new Account();
		PlayerItem pi = new PlayerItem(i, a, 4);
		pi.setAmount(-1);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void zero_amount_test() {
		Item i = new Item();
		Account a = new Account();
		PlayerItem pi = new PlayerItem(i, a, 4);
		pi.setAmount(0);
	}
	
	@Test
	@SuppressWarnings("unused")
	public void instance_test() {
		Item i = new Item();
		Account a = new Account();
		PlayerItem pi = new PlayerItem(i, a, 2);
	}
	
	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("unused")
	public void null_instance_1_test() {
		PlayerItem pi = new PlayerItem(null, null, 0);
	}
	
	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("unused")
	public void null_instance_2_test() {
		Item i = new Item();
		PlayerItem pi = new PlayerItem(i, null, 0);
	}
	
	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("unused")
	public void null_instance_3_test() {
		Item i = new Item();
		Account a = new Account();
		PlayerItem pi = new PlayerItem(i, a, 0);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void null_acc_test() {
		PlayerItem pi = new PlayerItem();
		pi.setAccount(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void null_item_test() {
		PlayerItem pi = new PlayerItem();
		pi.setItem(null);
	}
	
}
