package net.bestia.zoneserver.script;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import net.bestia.zoneserver.manager.InventoryManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class ItemScript {
	
	private final String scriptName;
	
	private final Bindings bindings = new SimpleBindings();

	public ItemScript(String name, PlayerBestiaManager owner, InventoryManager inventory) {
		this.scriptName = name;
		
		bindings.put("owner", owner);
		bindings.put("inventory", inventory);
	}
	
	public String getScriptKey() {
		return "item";
	}

	public String getName() {
		return scriptName;
	}

	public Bindings getBindings() {
		return bindings;
	}
}
