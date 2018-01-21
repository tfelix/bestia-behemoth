package net.bestia.zoneserver.script.env;

import net.bestia.zoneserver.script.ScriptApi;

import java.util.Map;
import java.util.Objects;

public abstract class BaseScriptEnvironment implements ScriptEnv {

	private final ScriptApi scriptApi;

	public BaseScriptEnvironment(ScriptApi scriptApi) {

		this.scriptApi = Objects.requireNonNull(scriptApi);
	}

	@Override
	public void setupEnvironment(Map<String, Object> bindings) {
		bindings.put("BAPI", scriptApi);
	}
}
