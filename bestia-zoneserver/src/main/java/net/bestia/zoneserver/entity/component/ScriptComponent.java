package net.bestia.zoneserver.entity.component;

import akka.actor.ActorPath;
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

	/**
	 * Path to the actor which is operating the periodic script calls.
	 */
	private ActorPath scriptActorPath;

	/**
	 * Path to the actor which is watching the scripts lifetime.
	 */
	private ActorPath scriptLifetimeActorPath;

	public ScriptComponent(long id) {
		super(id);
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

	public void setScriptActorPath(ActorPath scriptActorPath) {
		this.scriptActorPath = scriptActorPath;
	}

	public ActorPath getScriptActorPath() {
		return scriptActorPath;
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

	public ActorPath getScriptLifetimeActorPath() {
		return scriptLifetimeActorPath;
	}

	public void setScriptLifetimeActorPath(ActorPath scriptLifetimeActorPath) {
		this.scriptLifetimeActorPath = scriptLifetimeActorPath;
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
