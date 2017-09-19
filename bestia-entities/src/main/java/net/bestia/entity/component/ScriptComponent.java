package net.bestia.entity.component;

import java.util.EnumMap;

/**
 * Holds various script callbacks for entities.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptComponent extends Component {
	
	public enum Callback {
		ON_INTERVAL,
		ON_ENTER_AREA,
		ON_LEAVE_AREA,
		ON_TAKE_DMG,
		ON_ATTACK,
	}

	private static final long serialVersionUID = 1L;
	
	private EnumMap<Callback, String> callbackNames = new EnumMap<>(Callback.class);

	/**
	 * Unique script name. Each invocation of a script has a unique name which
	 * can be used to store and retrieve data.
	 */
	private String scriptUuid;

	public ScriptComponent(long id) {
		super(id);
		// no op.
	}

	public String getCallbackName(Callback c) {
		return callbackNames.get(c);
	}
	
	public void setCallbackName(Callback c, String name) {
		callbackNames.put(c, name);
	}
	
	public void removeCallback(Callback c) {
		callbackNames.remove(c);
	}

	public String getScriptUuid() {
		return scriptUuid;
	}

	public void setScriptUUID(String scriptUUID) {
		this.scriptUuid = scriptUUID;
	}
}
