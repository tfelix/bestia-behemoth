package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.manager.InventoryManager;

public class ItemScript extends Script {

	public ItemScript(String name, BestiaManager owner, InventoryManager inventory) {
		super(name);
		addBinding("target", owner);
		addBinding("inventory", inventory);
	}
	
	public ItemScript() {
		// no op.
	}

	@Override
	protected String getScriptPreKey() {
		return "item";
	}
}
