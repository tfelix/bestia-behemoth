package net.bestia.zoneserver.ecs.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.InputMessage;
import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.entity.SpriteType;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.messaging.MessageHandler;
import net.bestia.zoneserver.messaging.routing.DynamicBestiaIdMessageFilter;
import net.bestia.zoneserver.messaging.routing.MessageAndFilter;
import net.bestia.zoneserver.messaging.routing.MessageDirectDescandantFilter;
import net.bestia.zoneserver.messaging.routing.MessageIdFilter;
import net.bestia.zoneserver.messaging.routing.MessageRouter;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * The {@link PlayerBestiaSpawnManager} hooks itself into the message processing
 * and listens to {@link LoginBroadcastMessage}s. If it receives such a message
 * it will check if there are any player bestias from this account located on
 * its managed map and if so it will spawn them and send the player a info
 * message about the bestia which is now possibly under his command.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class PlayerBestiaSpawnManager extends BaseEntitySystem {

	private final static Logger LOG = LogManager.getLogger(PlayerBestiaSpawnManager.class);

	@Wire
	private CommandContext ctx;
	private AccountRegistry accountRegistry;

	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Attacks> attacksMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<PlayerBestia> playerBestiaMapper;
	private ComponentMapper<StatusPoints> statusPointMapper;
	private ComponentMapper<Visible> visibleMapper;
	private ComponentMapper<PlayerBestia> playerMapper;

	private final MessageHandler zoneProcessor;

	/**
	 * This filter used to route information for newly spawned bestias
	 * dynamically to this ECS zone system in order to process it.
	 */
	private final DynamicBestiaIdMessageFilter bestiaIdMessageFilter = new DynamicBestiaIdMessageFilter();

	private Archetype playerBestiaArchetype;

	/**
	 * Subscribes to all active players.
	 */
	private EntitySubscription activePlayerSubscription;

	/**
	 * Holds the reference between account id and bestia entity ids.
	 */
	private final Map<Long, Set<Integer>> accountBestiaRegister = new HashMap<>();
	private final Map<Integer, Integer> bestiaEntityRegister = new HashMap<>();

	public PlayerBestiaSpawnManager(MessageHandler zone) {
		super(Aspect.all(PlayerBestia.class));

		this.zoneProcessor = zone;

		// We dont tick. We are passiv.
		setEnabled(false);
	}

	@Override
	protected void initialize() {
		super.initialize();

		final MessageRouter router = ctx.getMessageRouter();

		// This manager needs to know about these two messages to create and
		// delete entities.
		final MessageIdFilter spawnMessageFilter = new MessageIdFilter();
		spawnMessageFilter.addMessageId(LogoutBroadcastMessage.MESSAGE_ID);

		// Prepare the message filter for the different zones. Depending on
		// active bestias on this zone messages can be re-routed to this
		// instance.
		final MessageAndFilter combineFilter = new MessageAndFilter();
		combineFilter.addFilter(new MessageDirectDescandantFilter(InputMessage.class));
		combineFilter.addFilter(bestiaIdMessageFilter);
		router.registerFilter(combineFilter, zoneProcessor);

		accountRegistry = ctx.getAccountRegistry();

		playerBestiaArchetype = new ArchetypeBuilder()
				.add(Position.class)
				.add(Attacks.class)
				.add(Bestia.class)
				.add(PlayerBestia.class)
				.add(StatusPoints.class)
				.add(Visible.class)
				.build(world);

		activePlayerSubscription = world.getAspectSubscriptionManager().get(Aspect.all(Visible.class, Active.class));
	}

	/**
	 * A new player bestia was added to this zone. Subscribe for input messages
	 * directed to this bestia.
	 */
	@Override
	protected void inserted(int entityId) {
		final PlayerBestiaEntityProxy pbm = playerMapper.get(entityId).playerBestiaManager;
		final int playerBestiaId = pbm.getPlayerBestiaId();
		final Long accountId = pbm.getAccountId();

		bestiaEntityRegister.put(playerBestiaId, entityId);
		bestiaIdMessageFilter.addPlayerBestiaId(playerBestiaId);
		accountRegistry.incrementBestiaOnline(accountId);

		// Add the entity to the register so it can be deleted.
		if (!accountBestiaRegister.containsKey(accountId)) {
			accountBestiaRegister.put(accountId, new HashSet<>());
		}
		accountBestiaRegister.get(accountId).add(entityId);
	}

	/**
	 * If a player bestia was removed from the ECS we want to unsubscribe from
	 * the messages for this particular bestia.
	 */
	@Override
	protected void removed(int entityId) {
		final PlayerBestiaEntityProxy pbm = playerMapper.get(entityId).playerBestiaManager;
		final int playerBestiaId = pbm.getPlayerBestiaId();
		final Long accountId = pbm.getAccountId();

		bestiaEntityRegister.remove(playerBestiaId);
		bestiaIdMessageFilter.removePlayerBestiaId(playerBestiaId);
		accountRegistry.decrementBestiaOnline(accountId);

		accountBestiaRegister.get(accountId).remove(entityId);
		if (accountBestiaRegister.get(accountId).size() == 0) {
			accountBestiaRegister.remove(accountId);
		}
	}

	@Override
	protected void processSystem() {
		// no op (disabled)
	}

	public void spawnBestia(net.bestia.model.domain.PlayerBestia pb) {
		final Long accId = pb.getOwner().getId();
		final Entity pbEntity = world.createEntity(playerBestiaArchetype);

		final PlayerBestiaEntityProxy pbm = new PlayerBestiaEntityProxy(pb,
				world,
				pbEntity,
				ctx.getServer(),
				ctx.getServiceLocator());

		playerBestiaMapper.get(pbEntity).playerBestiaManager = pbm;
		// Need to use bestia since PBM reads information only from the entity
		// position which is obviously not set yet.
		// TODO Der PlayerBestiaManager sollte für das Updaten verantwortlich
		// sein.
		positionMapper.get(pbEntity).position = new Vector2(pb.getCurrentPosition().getX(),
				pb.getCurrentPosition().getY());
		attacksMapper.get(pbEntity).addAll(pbm.getAttackIds());
		bestiaMapper.get(pbEntity).bestiaManager = pbm;

		final Visible visible = visibleMapper.get(pbEntity);
		visible.sprite = pbm.getPlayerBestia().getOrigin().getSprite();
		visible.spriteType = SpriteType.MOB_MULTI;

		// We need to check the bestia if its the master bestia. It will get
		// marked as active initially.
		final net.bestia.model.domain.PlayerBestia master = pb.getOwner().getMaster();
		final boolean isMaster = master.equals(pb);

		if (isMaster) {

			pbEntity.edit().create(Active.class);

			final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
			final InventoryProxy invManager = new InventoryProxy(pbm, invService, ctx.getServer());
			final Message invListMessage = invManager.getInventoryListMessage();
			ctx.getServer().sendMessage(invListMessage);

		}

		// Send a update to client so he can pick up the new bestia.
		final BestiaInfoMessage infoMsg = new BestiaInfoMessage();
		infoMsg.setAccountId(accId);
		infoMsg.setBestia(pbm.getPlayerBestia(), pbm.getStatusPoints());
		infoMsg.setIsMaster(isMaster);
		ctx.getServer().sendMessage(infoMsg);

		// Now set all the needed values.
		LOG.trace("Spawning player bestia: {}.", pb);
	}
	
	/**
	 * Sends the given message to all active bestias in sight.
	 * 
	 * @param source
	 *            The entity from which the sight range will be calculated.
	 * @param msg
	 */
	public void sendMessageToSightrange(int source, AccountMessage msg) {
		final Position sourcePosition = positionMapper.getSafe(source);

		if (sourcePosition == null) {
			LOG.warn("Entity has no position. Can not send messages.");
			return;
		}

		final IntBag receivers = getActivePlayersInSight(sourcePosition);

		for (int i = 0; i < receivers.size(); i++) {
			final int receiverId = receivers.get(i);

			final PlayerBestia pb = playerBestiaMapper.get(receiverId);
			final long accId = pb.playerBestiaManager.getAccountId();
			msg.setAccountId(accId);

			ctx.getServer().sendMessage(msg);
		}
	}

	/**
	 * Gets the entity id for the given player bestia id in this system. If the
	 * bestia is not spawned/unknown a 0 is returned.
	 * 
	 * @param playerBestiaId
	 *            The player bestia id.
	 * @return The entity id in the ECS or 0 if the bestia is not known.
	 */
	public int getEntityIdFromBestia(int playerBestiaId) {
		final Integer id = bestiaEntityRegister.get(playerBestiaId);
		if (id == null) {
			return 0;
		} else {
			return id.intValue();
		}
	}

	/**
	 * Returns the player bestia manager for a given id of this bestia.
	 * 
	 * @param playerBestiaId
	 * @return
	 */
	public PlayerBestiaEntityProxy getPlayerBestiaManager(int playerBestiaId) {
		final int entityId = getEntityIdFromBestia(playerBestiaId);
		final Entity entity = world.getEntity(entityId);

		if (entity == null) {
			// Should not happen i guess.
			return null;
		}

		return playerBestiaMapper.get(entity).playerBestiaManager;
	}

	/**
	 * Removes all bestia for a given account.
	 * 
	 * @param accountId
	 */
	public void despawnAllBestias(long accountId) {
		final Set<Integer> entityIds = accountBestiaRegister.get(accountId);

		// Might happen if a connection is dropped too late/message arriving too
		// late and the bestias where already all deleted. In this case do
		// nothing.
		if (entityIds == null) {
			return;
		}

		for (Integer id : entityIds) {
			try {
				world.delete(id);
			} catch (RuntimeException ex) {
				LOG.error("Could not delete. FIXIT", ex);
			}
			LOG.trace("Despawning player bestia (entity id: {})", id);
		}
	}

	/**
	 * Returns a list with all active player bestia entities.
	 * 
	 * @return
	 */
	public IntBag getActivePlayers() {
		return activePlayerSubscription.getEntities();
	}

	/**
	 * Tracks all active players in sight.
	 * 
	 * @param pos
	 * @return
	 */
	public IntBag getActivePlayersInSight(Position pos) {
		return activePlayerSubscription.getEntities();
	}

}
