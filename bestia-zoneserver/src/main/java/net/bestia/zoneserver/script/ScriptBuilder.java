package net.bestia.zoneserver.script;

import java.util.HashMap;
import java.util.Map;

import net.bestia.zoneserver.proxy.Entity;
import net.bestia.zoneserver.proxy.InventoryProxy;

/**
 * TODO Vielleicht als interne Klasse von Script.
 * @author Thomas
 *
 */
public class ScriptBuilder {

	int x;
	int y;
	Entity target;
	Entity owner;
	InventoryProxy inventory;
	ScriptApi api;
	String name;
	String scriptPrefix;
	
	Map<String, Object> miscBindings = new HashMap<>();

	public ScriptBuilder setTargetCoordinates(int x, int y) {
		this.x = x;
		this.y = y;
		return this;
	}
	
	public ScriptBuilder setScriptPrefix(String scriptPrefix) {
		this.scriptPrefix = scriptPrefix;
		return this;
	}

	public ScriptBuilder setTargetEntity(Entity target) {
		this.target = target;
		return this;
	}

	public ScriptBuilder setOwnerEntity(Entity owner) {
		this.owner = owner;
		return this;
	}

	public ScriptBuilder setInventory(InventoryProxy inventory) {
		this.inventory = inventory;
		return this;
	}
	
	public ScriptBuilder setBinding(String key, Object obj) {
		miscBindings.put(key, obj);
		return this;
	}

	public ScriptBuilder setApi(ScriptApi api) {
		this.api = api;
		return this;
	}
	
	public ScriptBuilder setName(String name) {
		this.name = name;
		return this;
	}

	public Script build() {
		return new Script(this);
	}
}
