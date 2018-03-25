package net.bestia.zoneserver.script.env;

import net.bestia.model.geometry.Point;

import java.util.Map;

public class ItemScriptEnv extends ScriptEnv {

	private final Point targetPosition;
	private final long userId;
	private final long targetId;

	public ItemScriptEnv(long userId, long targetId
  ) {
		this(userId, targetId, null);
	}

	public ItemScriptEnv(long userId, Point targetPosition) {
		this(userId, 0, targetPosition);
	}

	public ItemScriptEnv(
			long userId,
			long targetId,
			Point targetPosition) {

		this.userId = userId;
		this.targetId = targetId;
		this.targetPosition = targetPosition;
	}

  @Override
  protected void customEnvironmentSetup(Map<String, Object> bindings) {
    bindings.put("SELF", userId);
    bindings.put("TARGET_ENTITY", targetId);
    bindings.put("TARGET_POSITION", targetPosition);
  }
}
