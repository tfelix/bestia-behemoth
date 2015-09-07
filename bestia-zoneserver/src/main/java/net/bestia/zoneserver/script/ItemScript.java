package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.InventoryManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class ItemScript extends Script {

	public ItemScript(String name, PlayerBestiaManager owner, InventoryManager inventory) {
		super(name);
		
		addBinding("owner", owner);
		addBinding("inventory", inventory);
	}
	
	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.script.Script#getScriptKey()
	 */
	@Override
	public String getScriptKey() {
		return "item";
	}
}
