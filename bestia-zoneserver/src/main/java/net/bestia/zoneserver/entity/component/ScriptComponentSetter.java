package net.bestia.zoneserver.entity.component;

import java.util.Objects;
import java.util.UUID;

import net.bestia.zoneserver.script.ScriptType;

/**
 * Fills a script component with values.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptComponentSetter extends ComponentSetter<ScriptComponent> {

	private String name;
	private ScriptType type;

	public ScriptComponentSetter(String scriptName, ScriptType type) {
		super(ScriptComponent.class);

		this.name = Objects.requireNonNull(scriptName);
		this.type = type;
	}

	@Override
	protected void performSetting(ScriptComponent comp) {

		comp.setScriptUUID(UUID.randomUUID().toString());
		comp.setScriptName(name);
		comp.setScriptType(type);

	}

}
