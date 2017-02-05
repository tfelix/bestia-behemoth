package net.bestia.zoneserver.script;

import java.util.Objects;

import javax.script.CompiledScript;
import javax.script.Invocable;

abstract class Script {

	private ScriptCache cache;
	private final String name;

	public Script(String name) {
		
		this.name = Objects.requireNonNull(name);
	}

	public void setScriptCache(ScriptCache cache) {
		this.cache = cache;
	}

	abstract protected ScriptType getScriptType();

	protected CompiledScript getScript() {
		return cache.getScript(getScriptType(), name);
	}
	
	protected Invocable getInvocable() {
		final CompiledScript script = getScript();
		return (Invocable) script.getEngine();
	}
}