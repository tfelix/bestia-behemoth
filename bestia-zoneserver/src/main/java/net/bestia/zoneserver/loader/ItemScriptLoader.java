package net.bestia.zoneserver.loader;

import java.io.File;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.script.ExecutionBindings;

/**
 * Kommentieren. TODO
 * @author Thomas
 *
 */
public class ItemScriptLoader extends ScriptLoader {

	public ItemScriptLoader(File baseDir, CommandContext ctx) {
		super(baseDir, ctx);
		// no op.
	}

	@Override
	public String getKey() {
		return "item";
	}

	@Override
	public ExecutionBindings getExecutionBindings() {
		final ExecutionBindings bindings = new ExecutionBindings();
		
		bindings.addBinding("msg", ctx.getServer());
		
		return bindings;
	}
}
