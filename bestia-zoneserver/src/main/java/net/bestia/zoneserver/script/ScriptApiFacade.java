package net.bestia.zoneserver.script;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.zoneserver.entity.EntityService;

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
	
	private final EntityService entityService;
	//private final 
	
	@Autowired
	public ScriptApiFacade(EntityService entityService) {
		
		this.entityService = Objects.requireNonNull(entityService);
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
	public ScriptEntity createSpellEntity(CollisionShape shape, String spriteName, int baseDuration) {
		// TODO Auto-generated method stub
		return null;
	}

}
