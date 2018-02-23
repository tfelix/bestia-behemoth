package net.bestia.zoneserver.script.env;

import net.bestia.zoneserver.script.ScriptApi;

import java.util.Map;
import java.util.Objects;

public class IntervalScriptEnv extends BaseScriptEnvironment {

	private final String uuid;

	public IntervalScriptEnv(ScriptApi scriptApi, String uuid) {
		super(scriptApi);

		this.uuid = Objects.requireNonNull(uuid);
	}

	@Override
	public void setupEnvironment(Map<String, Object> bindings) {
		super.setupEnvironment(bindings);

		bindings.put("SCRIPT_UUID", uuid);
	}
}
