package net.bestia.entity.component;

import java.util.UUID;


/**
 * Fills a script component with values.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptComponentSetter extends ComponentSetter<ScriptComponent> {

	public ScriptComponentSetter(String scriptName) {
		super(ScriptComponent.class);
		// no op.
	}

	@Override
	protected void performSetting(ScriptComponent comp) {

		comp.setScriptUUID(UUID.randomUUID().toString());

	}

}
