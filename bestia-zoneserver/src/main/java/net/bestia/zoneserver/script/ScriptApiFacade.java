package net.bestia.zoneserver.script;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.ScriptEntityFactory;
import net.bestia.zoneserver.entity.components.PositionComponent;

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

	private static final Logger LOG = LoggerFactory.getLogger("script");

	private final ScriptEntityFactory scriptEntityFactory;
	private final ScriptService scriptService;
	private final EntityService entityService;

	/**
	 * 
	 * @param entityService
	 * @param scriptService
	 *            The service the scripts have to access. It creates a circular
	 *            dependency thus needs to be lazy initialized.
	 */
	@Autowired
	public ScriptApiFacade(EntityService entityService, @Lazy ScriptService scriptService) {

		this.scriptEntityFactory = new ScriptEntityFactory(entityService);
		this.scriptService = Objects.requireNonNull(scriptService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	public void info(String text) {
		LOG.info(text);
	}

	@Override
	public void debug(String text) {
		LOG.debug(text);
	}

	public void saveData(String scriptUid, String json) {

		LOG.error("Not implemented yet");
		// SCRIPT_LOG.debug("Saving script data for script: {} data: {}.",
		// scriptUid, json);
	}

	public String loadData(String scriptUid) {

		LOG.error("Not implemented yet");
		return "";
		// SCRIPT_LOG.debug("Loading script data for script: {} data: {}.",
		// scriptUid, "nothing");
	}

	@Override
	public long createEntity(CollisionShape shape) {
		LOG.trace("Creating a new entity.");

		final Entity entity = scriptEntityFactory.build(shape);

		return entity.getId();
	}

	@Override
	public void setLivetime(long entityId, int livetimeMs) {
		LOG.trace("Entity: {}. Sets lifetime: {} ms.", entityId, livetimeMs);

	}

	@Override
	public void kill(long entityId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setInterval(long entityId, String scriptName, String callbackName, int delayMs) {
		LOG.trace("Entity: {}. Set interval function callback name: {}.", entityId, callbackName);

		final Entity entity = entityService.getEntity(entityId);

		if (entity == null) {
			LOG.warn("Unknown entity id: {}.", entityId);
			return;
		}

		scriptService.startScriptInterval(entity, delayMs, callbackName);
	}

	@Override
	public void setOnEnter(long entityId, String callbackName) {
		LOG.trace("Script Entity: {}. setOnEnter called.", entityId);

	}

	@Override
	public void setOnLeave(long entityId, String callbackName) {
		LOG.trace("Script Entity: {}. setOnLeave called.", entityId);

	}

	@Override
	public void setVisual(long entityId, String spriteName) {
		LOG.trace("Entity: {}. Set visual: {}", entityId, spriteName);

	}

	@Override
	public void playAnimation(long entityId, String animationName) {
		LOG.trace("Entity: {}. Play animation: {}", entityId, animationName);

	}

	@Override
	public void setPosition(long entityId, long x, long y) {
		LOG.trace("Entity: {}. Sets position x: {} y: {}.", entityId, x, y);

		entityService.getComponent(entityId, PositionComponent.class).ifPresent(pos -> {
			pos.setPosition(x, y);
			entityService.saveComponent(pos);
		});
	}

	@Override
	public void setShape(long enitityId, CollisionShape shape) {
		LOG.trace("Entity: {}. Sets shape: {}.", enitityId, shape);

	}

}
