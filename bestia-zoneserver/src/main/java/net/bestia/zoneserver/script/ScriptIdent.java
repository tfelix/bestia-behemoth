package net.bestia.zoneserver.script;

import java.util.Objects;

class ScriptIdent {

	private final ScriptType type;
	private final String name;
	private final String functionName;

	public ScriptIdent(ScriptType type, String name, String functionName) {

		this.type = Objects.requireNonNull(type);
		this.name = Objects.requireNonNull(name);
		this.functionName = Objects.requireNonNull(functionName);
	}

	public ScriptType getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public String getFunctionName() {
		return functionName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, name, functionName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScriptIdent other = (ScriptIdent) obj;
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
		if (type != other.type)
			return false;
		return true;
	}
}