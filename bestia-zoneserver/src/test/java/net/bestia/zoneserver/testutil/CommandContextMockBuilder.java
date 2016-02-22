package net.bestia.zoneserver.testutil;

import java.io.File;
import java.io.IOException;

import org.mockito.Mockito;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.script.ScriptManager;

public class CommandContextMockBuilder {
	
	private BestiaConfiguration config;
	private Zoneserver zoneserver;
	private ScriptManager scriptManager;
	
	public void setConfig(BestiaConfiguration config) {
		this.config = config;
	}
	
	public void setZoneserver(Zoneserver server) {
		this.zoneserver = server;
	}
	
	public void setScriptManager(ScriptManager manager) {
		this.scriptManager = manager;
	}

	public CommandContext getContext() {
		
		// Build the context object.
		if(config == null) {
			config = getConfiguration();
		}
		
		if(zoneserver == null) {
			zoneserver = getZoneserver();
		}
		
		if(scriptManager == null) {
			scriptManager = getScriptManager();
		}
		
		final CommandContext ctx = new CommandContext(config, zoneserver, scriptManager);
		
		return ctx;
	}
	
	private BestiaConfiguration getConfiguration() {
		final BestiaConfiguration config = new BestiaConfiguration();
		final ClassLoader classLoader = getClass().getClassLoader();
		final File propFile = new File(classLoader.getResource("bestia.properties").getFile());
		try {
			config.load(propFile);
		} catch (IOException e) {
			return null;
		}
		
		return config;
	}
	
	private Zoneserver getZoneserver() {
		final Zoneserver server = Mockito.mock(Zoneserver.class);
		
		return server;
	}
	
	private ScriptManager getScriptManager() {
		final ScriptManager manager = Mockito.mock(ScriptManager.class);
		
		return manager;
	}
}
