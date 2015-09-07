package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class AttackScript extends Script {

	public AttackScript(String name, PlayerBestiaManager owner) {
		super(name);
		
		addBinding("owner", owner);
	}

	@Override
	public String getScriptKey() {
		return "attack";
	}
}
