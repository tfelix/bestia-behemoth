package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.manager.InventoryManager;

public class ItemScript2 extends Script2 {

	public ItemScript2(String name, BestiaManager owner, InventoryManager inventory) {
		super(name);
		addBinding("owner", owner);
		addBinding("inventory", inventory);
	}
	
	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.script.Script#getScriptKey()
	 */
	@Override
	public String getScriptKey() {
		return String.format("item.%s", getName());
	}

	@Override
	public String getScriptSubPath() {
		// TODO Auto-generated method stub
		return null;
	}
}
