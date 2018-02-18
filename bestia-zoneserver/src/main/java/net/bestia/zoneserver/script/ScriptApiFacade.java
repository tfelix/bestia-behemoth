package net.bestia.zoneserver.script;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.factory.MobFactory;
import net.bestia.entity.factory.ScriptEntityFactory;
import net.bestia.messages.MessageApi;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.ScriptVarDAO;
import net.bestia.model.domain.ScriptVar;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.battle.BattleService;
import net.bestia.zoneserver.entity.MovingService;

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
	private static final Logger LOG = LoggerFactory.getLogger(ScriptApiFacade.class);

	private ScriptEntityFactory scriptEntityFactory;
	private EntityService entityService;
	private MessageApi akkaApi;
	private BattleService battleService;
	private ScriptService scriptService;
	private MovingService moveService;
	private MobFactory mobFactory;
	private ScriptVarDAO scriptVarDao;

	public ScriptApiFacade() {
		// no op.
	}

	@Autowired
	public void setScriptVarDao(ScriptVarDAO scriptVarDao) {
		this.scriptVarDao = scriptVarDao;
	}

	@Autowired
	public void setMobFactory(MobFactory mobFactory) {
		this.mobFactory = mobFactory;
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
	public void setAkkaApi(MessageApi akkaApi) {
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
	public void setMoveService(MovingService moveService) {
		this.moveService = moveService;
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
	public long createEntity(CollisionShape shape) {
		LOG.trace("Creating a new raw entity.");

		final Entity entity = scriptEntityFactory.build(shape);

		return entity.getId();
	}

	@Override
	public void kill(long entityId) {
		LOG.trace("Killing entity: {}.", entityId);
		final Entity entity = getEntityById(entityId);
		battleService.killEntity(entity);
	}

	@Override
	public void setInterval(long entityId, String scriptName, int delayMs) {
		LOG.trace("Entity: {}. Set interval function callback name: {}.", entityId, scriptName);
		scriptService.startScriptInterval(getEntityById(entityId), delayMs, scriptName);
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

	private Entity getEntityById(long eid) {
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

		final Entity entity = entityService.getEntity(entityId);
		entityService.delete(entity);

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
		akkaApi.sendToClient(accId, chatMsg);
	}

	@Override
	public long spawnMob(String mobDbName, long x, long y) {
		LOG.trace("spawnMob: mobDbName: {} x: {} y: {}.", mobDbName, x, y);

		final Entity e = mobFactory.build(mobDbName, x, y);

		if (e == null) {
			return 0;
		} else {
			return e.getId();
		}
	}

	@Override
	public void setScriptVar(String key, String data) {
		LOG.trace("setScriptVar: key: {} data: {}.", key, data);

		ScriptVar svar = scriptVarDao.findByScriptKey(key);

		if (svar == null) {
			svar = new ScriptVar(key, data);
		}

		scriptVarDao.save(svar);
	}

	@Override
	public String getScriptVar(String key) {
		final ScriptVar svar = scriptVarDao.findByScriptKey(key);

		LOG.trace("getScriptVar: key: {} svar: {}.", key, svar);

		if (svar == null) {
			return null;
		} else {
			return svar.getData();
		}
	}

	@Override
	public boolean exists(long entityId) {
		LOG.trace("exists: entityId: {}", entityId);
		return entityService.getEntity(entityId) != null;
	}
}
