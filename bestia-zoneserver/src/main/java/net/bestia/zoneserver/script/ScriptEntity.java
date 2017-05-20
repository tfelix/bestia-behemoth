package net.bestia.zoneserver.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.entity.Entity;

/**
 * This is a wrapper class for a entity to be used in scripts. It holds various
 * shortcut methods which will call differnt services. It serves as a facade.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptEntity {
	
	private static final Logger LOG = LoggerFactory.getLogger(ScriptEntity.class);
	
	private final Entity entity = null;

	public ScriptEntity setOnTouch(Runnable callback) {
		LOG.trace("Script Entity: {}. setOnTouch called.", entity);
		
		return this;
	}
	
	public ScriptEntity setInterval(int delay, Runnable callback) {
		LOG.trace("Script Entity: {}. setInterval called.", entity);
		
		return this;
	}
}
