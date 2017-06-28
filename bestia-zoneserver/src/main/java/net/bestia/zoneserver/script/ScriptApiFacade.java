package net.bestia.zoneserver.script;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.MovingEntityService;
import net.bestia.entity.ScriptEntityFactory;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.entity.EntityDeleteWorker;
import net.bestia.zoneserver.battle.BattleService;

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

	private ScriptEntityFactory scriptEntityFactory;
	private EntityService entityService;
	private ZoneAkkaApi akkaApi;
	private BattleService battleService;
	private ScriptService scriptService;
	private MovingEntityService moveService;

	public ScriptApiFacade() {
		// no op.
	}

	@Autowired
	public void setScriptEntityFactory(ScriptEntityFactory scriptEntityFactory) {
		this.scriptEntityFactory = scriptEntityFactory;
	}

	@Autowired
	public void setEntityService(EntityService entityService) {
		this.entityService = entityService;
	}

	@Autowired
	public void setAkkaApi(ZoneAkkaApi akkaApi) {
		this.akkaApi = akkaApi;
	}

	@Autowired
	public void setBattleService(BattleService battleService) {
		this.battleService = battleService;
	}

	@Autowired
	public void setScriptService(ScriptService scriptService) {
		this.scriptService = scriptService;
	}

	@Autowired
	public void setMoveService(MovingEntityService moveService) {
		this.moveService = moveService;
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

	@Override
	public void delete(long entityId) {
		akkaApi.sendToActor(EntityDeleteWorker.NAME, entityId);
	}

	@Override
	public void sendMessage(long playerEntityId, String message, String modeStr) {
		if (message == null) {
			LOG.warn("sendMessage: Message can not be null.");
			return;
		}

		// Find the account id.
		Optional<PlayerComponent> playerComp = entityService
				.getComponent(playerEntityId, PlayerComponent.class);

		if (!playerComp.isPresent()) {
			LOG.warn("sendMessage: EID is no player entity id.");
			return;
		}

		final long accId = playerComp.get().getOwnerAccountId();

		// Get the chat mode.
		ChatMessage.Mode mode = ChatMessage.Mode.SYSTEM;
		try {
			mode = ChatMessage.Mode.valueOf(modeStr.toUpperCase());
		} catch (NullPointerException | IllegalArgumentException e) {
			LOG.warn("sendMessage: Invalid chat mode. Using default: SYSTEM.");
		}

		final ChatMessage chatMsg = new ChatMessage(accId, playerEntityId, message, mode);
		akkaApi.sendToClient(chatMsg);
	}
}
