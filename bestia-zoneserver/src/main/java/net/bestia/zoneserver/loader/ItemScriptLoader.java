package net.bestia.zoneserver.loader;

import java.io.File;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import net.bestia.zoneserver.command.CommandContext;

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
	public Bindings getExecutionBindings() {
		final Bindings bindings = new SimpleBindings();
		
		bindings.put("msg", ctx.getServer());
		
		return bindings;
	}
}
