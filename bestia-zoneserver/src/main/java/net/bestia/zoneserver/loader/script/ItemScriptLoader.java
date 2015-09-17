package net.bestia.zoneserver.loader.script;

import java.io.File;

import net.bestia.zoneserver.command.CommandContext;

/**
 * Loads all the item scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ItemScriptLoader extends ScriptLoader {

	public ItemScriptLoader(File baseDir, CommandContext ctx) {
		super(baseDir, ctx);

		addBinding("msg", ctx.getServer());
	}

	@Override
	public String getKey() {
		return "item";
	}
}
