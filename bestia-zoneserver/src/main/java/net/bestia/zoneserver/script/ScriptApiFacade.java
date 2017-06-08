package net.bestia.zoneserver.script;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.entity.EntityLifetimeWatchdogActor;
import net.bestia.zoneserver.battle.BattleService;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.MovingEntityService;
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

	private static final Logger LOG = LoggerFactory.getLogger("script");

	private final ScriptEntityFactory scriptEntityFactory;
	private final EntityService entityService;
	private final ZoneAkkaApi akkaApi;
	private final BattleService battleService;
	private final ScriptService scriptService;
	private final MovingEntityService moveService;

	/**
	 * 
	 * @param entityService
	 * @param scriptService
	 *            The service the scripts have to access. It creates a circular
	 *            dependency thus needs to be lazy initialized.
	 */
	@Autowired
	public ScriptApiFacade(
			EntityService entityService,
			BattleService battleService,
			@Lazy ScriptService scriptService,
			MovingEntityService moveService,
			ZoneAkkaApi akkaApi) {

		this.scriptEntityFactory = new ScriptEntityFactory(entityService);
		this.entityService = Objects.requireNonNull(entityService);
		this.battleService = Objects.requireNonNull(battleService);
		this.scriptService = Objects.requireNonNull(scriptService);
		this.moveService = Objects.requireNonNull(moveService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
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

		final ActorRef watchdog = akkaApi.startUnnamedActor(EntityLifetimeWatchdogActor.class);
		final ScriptIntervalMessage message = new ScriptIntervalMessage(entityId, livetimeMs);
		watchdog.tell(message, ActorRef.noSender());
	}

	@Override
	public void kill(long entityId) {
		LOG.trace("Killing entity: {}.", entityId);
		final Entity entity = getEntityFromId(entityId);
		battleService.killEntity(entity);
	}

	@Override
	public void setInterval(long entityId, String scriptName, String callbackName, int delayMs) {
		LOG.trace("Entity: {}. Set interval function callback name: {}.", entityId, callbackName);
		scriptService.startScriptInterval(getEntityFromId(entityId), delayMs, callbackName);
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
		moveService.moveToPosition(entityId, new Point(x, y));
	}

	@Override
	public void setShape(long enitityId, CollisionShape shape) {
		LOG.trace("Entity: {}. Sets shape: {}.", enitityId, shape);

	}

	private Entity getEntityFromId(long eid) {
		final Entity e = entityService.getEntity(eid);
		if (e == null) {
			throw new IllegalArgumentException("Unknown entity id: " + eid);
		}
		return e;
	}

	@Override
	public List<Long> findEntities(long x, long y, long width, long height) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isEntityTypeOf(long entityId, String type) {
		// TODO Auto-generated method stub
		return false;
	}
}
