package net.bestia.entity.component;

import java.util.EnumMap;

import com.google.common.base.Objects;

/**
 * Holds various script callbacks for entities.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptComponent extends Component {

	/**
	 * Callback types of the different scripts. This is used to register
	 * different callbacks for different hooks in the bestia engine. For further
	 * information which variables are available for the different script
	 * environments consult the various script environment implementations in
	 * the bestia-zone module.
	 *
	 */
	public enum Callback {

		/**
		 * Script is called on a regular time basis.
		 */
		ON_INTERVAL,

		/**
		 * Script gets called for every entity entering the area.
		 */
		ON_ENTER_AREA,

		/**
		 * Script gets called for every entity leaving the area.
		 */
		ON_LEAVE_AREA,

		/**
		 * Script is called if the entity is damage awarded.
		 */
		ON_TAKE_DMG,

		/**
		 * This hook gets called before the damage is calculated to the script
		 * so the script can influence the damage calculation.
		 */
		ON_BEFORE_TAKE_DMG,

		/**
		 * The script is called if a attack is about to be processed.
		 */
		ON_ATTACK,

		/**
		 * Script is called if an item is picked up by a player.
		 */
		ON_ITEM_PICKUP,

		/**
		 * Script is called if a player drops an item.
		 */
		ON_ITEM_DROP
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

	@Override
	public String toString() {
		return String.format("ScriptComponent[callbacks: %s]", callbackNames.toString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(callbackNames, scriptUuid);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof ScriptComponent)) {
			return false;
		}
		final ScriptComponent other = (ScriptComponent) obj;
		return Objects.equal(callbackNames, other.callbackNames)
				&& Objects.equal(scriptUuid, other.scriptUuid);
	}

}
