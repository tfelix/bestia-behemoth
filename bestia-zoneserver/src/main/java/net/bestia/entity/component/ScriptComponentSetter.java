package net.bestia.entity.component;

import java.util.UUID;

import net.bestia.zoneserver.script.ScriptType;

/**
 * Fills a script component with values.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptComponentSetter extends ComponentSetter<ScriptComponent> {

	public ScriptComponentSetter(String scriptName, ScriptType type) {
		super(ScriptComponent.class);
		// no op.
	}

	@Override
	protected void performSetting(ScriptComponent comp) {

		comp.setScriptUUID(UUID.randomUUID().toString());

	}

}
