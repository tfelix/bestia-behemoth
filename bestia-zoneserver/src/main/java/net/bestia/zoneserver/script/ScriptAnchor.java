package net.bestia.zoneserver.script;

import java.util.Objects;

class ScriptAnchor {

	private final String name;
	private final String functionName;

	public ScriptAnchor(String name, String functionName) {

		this.name = Objects.requireNonNull(name);
		this.functionName = Objects.requireNonNull(functionName);
	}

	public static ScriptAnchor fromString(String anchorStr) {
		final String[] token = anchorStr.split(":");
		if(token.length != 2) {
			throw new IllegalArgumentException("Invalid anchor string.");
		}

		return new ScriptAnchor(token[0], token[1]);
	}

	public String getScriptName() {
		return name;
	}

	public String getFunctionName() {
		return functionName;
	}

	/**
	 * Creates a specialized string for safe the anchor.
	 */
	public String getAnchorString() {
		return String.format("%s:%s", name, functionName);
	}

	@Override
	public String toString() {
		return String.format("ScriptAnchor[name: %s, fn: %s]", name, functionName);
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
		ScriptAnchor other = (ScriptAnchor) obj;
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