package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.BestiaManager;

public class AttackScript extends Script {

	public AttackScript(String name, BestiaManager owner) {
		super(name);
		
		addBinding("owner", owner);
	}

	@Override
	public String getScriptKey() {
		return "attack";
	}
}
