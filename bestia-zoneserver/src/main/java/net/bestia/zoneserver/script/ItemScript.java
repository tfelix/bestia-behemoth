package net.bestia.zoneserver.script;

import net.bestia.zoneserver.proxy.BestiaEntityProxy;
import net.bestia.zoneserver.proxy.InventoryProxy;

public class ItemScript extends Script {

	public ItemScript(String name, BestiaEntityProxy owner, MapScriptAPI mapApi, InventoryProxy inventory) {
		super(name);
		addBinding("api", mapApi);
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
