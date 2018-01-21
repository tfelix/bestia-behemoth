package net.bestia.zoneserver.script.env;

import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.script.ScriptApi;

import java.util.Map;

public class ItemScriptEnvironment extends BaseScriptEnvironment {

	private final Point targetPosition;
	private final long userId;
	private final long targetId;

	public ItemScriptEnvironment(ScriptApi scriptApi, long userId, long targetId) {
		this(scriptApi, userId, targetId, null);
	}

	public ItemScriptEnvironment(ScriptApi scriptApi, long userId, Point targetPosition) {
		this(scriptApi, userId, 0, targetPosition);
	}

	public ItemScriptEnvironment(
			ScriptApi scriptApi,
			long userId,
			long targetId,
			Point targetPosition) {
		super(scriptApi);

		this.userId = userId;
		this.targetId = targetId;
		this.targetPosition = targetPosition;
	}

	@Override
	public void setupEnvironment(Map<String, Object> bindings) {
		super.setupEnvironment(bindings);

		bindings.put("SELF", userId);
		bindings.put("TARGET_ENTITY", targetId);
		bindings.put("TARGET_POSITION", targetPosition);
	}
}
