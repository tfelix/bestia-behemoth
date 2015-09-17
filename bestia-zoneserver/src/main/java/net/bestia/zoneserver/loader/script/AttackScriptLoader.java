package net.bestia.zoneserver.loader.script;

import java.io.File;

import net.bestia.zoneserver.command.CommandContext;

/**
 * Loads all the attack scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackScriptLoader extends ScriptLoader {

	public AttackScriptLoader(File baseDir, CommandContext ctx) {
		super(baseDir, ctx);
		// TODO Do the bindings.
	}

	@Override
	public String getKey() {
		return "attack";
	}

}
