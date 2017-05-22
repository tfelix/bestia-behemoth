package net.bestia.zoneserver.script;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.ScriptEntityFactory;

/**
 * Bundles all kind of services to provide an extensive script API. This API is
 * bound to every script execution and can be used in order to interact with the
 * bestia server.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ScriptApiFacade implements ScriptApi {
	
	private static final Logger SCRIPT_LOG = LoggerFactory.getLogger("script");
	
	private final ScriptEntityFactory scriptEntityFactory;
	private final ScriptService scriptService;
	
	@Autowired
	public ScriptApiFacade(EntityService entityService, ScriptService scriptService) {
		
		this.scriptEntityFactory = new ScriptEntityFactory(entityService);
		this.scriptService = Objects.requireNonNull(scriptService);
	}

	@Override
	public void info(String text) {
		SCRIPT_LOG.info(text);
	}

	@Override
	public void debug(String text) {
		SCRIPT_LOG.debug(text);
	}

	@Override
	public ScriptEntityWrapper createSpellEntity(CollisionShape shape, String spriteName, int baseDuration) {
		SCRIPT_LOG.trace("Creating a new script entity.");
		
		final Entity entity = scriptEntityFactory.build(shape, "", null);
		
		final ScriptEntityWrapper entityWrapper = new ScriptEntityWrapper(entity, scriptService);
		
		return entityWrapper;
	}

}
