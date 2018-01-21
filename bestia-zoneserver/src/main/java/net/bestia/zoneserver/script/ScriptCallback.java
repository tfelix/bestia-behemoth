package net.bestia.zoneserver.script;

import java.util.Objects;

class ScriptCallback {

	private final String name;
	private final String functionName;

	public ScriptCallback(String name, String functionName) {

		this.name = Objects.requireNonNull(name);
		this.functionName = Objects.requireNonNull(functionName);
	}

	public String getName() {
		return name;
	}

	public String getFunctionName() {
		return functionName;
	}

	@Override
	public String toString() {
		return String.format("ScriptCallback[name: %s, fn: %s]", name, functionName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, functionName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScriptCallback other = (ScriptCallback) obj;
		if (functionName == null) {
			if (other.functionName != null)
				return false;
		} else if (!functionName.equals(other.functionName))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}