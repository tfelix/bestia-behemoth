package net.bestia.zoneserver.script.env;

import java.util.Map;

import net.bestia.zoneserver.battle.BattleService;

public class AttackScriptEnv implements ScriptEnv {
	
	private final BattleService battleService;
	
	private AttackScriptEnv(BattleService battleService) {
		
		this.battleService = battleService;
	}
	
	public AttackScriptEnv forAttackEntity() {
		return null;
	}

	@Override
	public void setupEnvironment(Map<String, Object> bindings) {
		
		// Attacker ID
		// Attack ID
		// Defender ID
		
		
		// TODO Auto-generated method stub
		
	}

}
