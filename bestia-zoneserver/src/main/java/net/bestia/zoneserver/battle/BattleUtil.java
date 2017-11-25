package net.bestia.zoneserver.battle;

final class BattleUtil {
	
	private BattleUtil() {
		// no op.
	}

	static float between(float min, float max, float val) {
		return Math.max(min, Math.min(max, val));
	}
}
