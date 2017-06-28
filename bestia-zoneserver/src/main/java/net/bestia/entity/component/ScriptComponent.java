package net.bestia.entity.component;

import net.bestia.zoneserver.script.ScriptType;

public class ScriptComponent extends Component {

	private static final long serialVersionUID = 1L;

	private String onIntervalCallbackName;
	private String onEnterCallbackName;
	private String onLeaveCallbackName;

	private String scriptName;
	private ScriptType scriptType;

	/**
	 * Unique script name. Each invocation of a script has a unique name which
	 * can be used to store and retrieve data.
	 */
	private String scriptUUID;

	public ScriptComponent(long id, long entityId) {
		super(id, entityId);
		// no op.
	}

	public String getScriptName() {
		return scriptName;
	}

	public void setScriptName(String scriptName) {
		this.scriptName = scriptName;
	}

	public ScriptType getScriptType() {
		return scriptType;
	}

	public void setScriptType(ScriptType scriptType) {
		this.scriptType = scriptType;
	}

	public String getOnIntervalCallbackName() {
		return onIntervalCallbackName;
	}

	public void setOnIntervalCallbackName(String callbackFunctionName) {
		this.onIntervalCallbackName = callbackFunctionName;
	}

	public String getScriptUUID() {
		return scriptUUID;
	}

	public void setScriptUUID(String scriptUUID) {
		this.scriptUUID = scriptUUID;
	}

	public String getOnLeaveCallbackName() {
		return onLeaveCallbackName;
	}

	public String getOnEnterCallbackName() {
		return onEnterCallbackName;
	}

	public void setOnEnterCallbackName(String onEnterCallbackName) {
		this.onEnterCallbackName = onEnterCallbackName;
	}

	public void setOnLeaveCallbackName(String onLeaveCallbackName) {
		this.onLeaveCallbackName = onLeaveCallbackName;
	}
}
