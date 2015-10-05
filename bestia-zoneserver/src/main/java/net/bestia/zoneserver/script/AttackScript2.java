package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.BestiaManager;

public class AttackScript2 extends Script2 {

	public AttackScript2(String name, BestiaManager owner) {
		super(name);
		
		addBinding("owner", owner);
	}

	@Override
	public String getScriptKey() {
		return "attack";
	}

	@Override
	public String getScriptSubPath() {
		// TODO Auto-generated method stub
		return null;
	}
}
