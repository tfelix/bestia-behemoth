package net.bestia.zoneserver.ecs.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.InputMessage;
import net.bestia.messages.login.LoginBroadcastMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.entity.EcsEntityFactory;
import net.bestia.zoneserver.ecs.entity.EntityFactory;
import net.bestia.zoneserver.ecs.entity.PlayerEntityBuilder;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.messaging.DynamicPathPredicate;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;
import net.bestia.zoneserver.zone.Zone;

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

	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<PlayerBestia> playerBestiaMapper;

	private EntityFactory entityFactory;

	/**
	 * Subscribes to all active players.
	 */
	private EntitySubscription activePlayerSubscription;

	/**
	 * Holds the reference between account id and bestia entity ids.
	 */
	private final Map<Long, Set<Integer>> accountBestiaRegister = new HashMap<>();
	private final Map<Integer, Integer> bestiaEntityRegister = new HashMap<>();
	
	private final Zone zone;
	private final DynamicPathPredicate pathFilter;

	public PlayerBestiaSpawnManager(Zone zone) {
		super(Aspect.all(PlayerBestia.class));
		
		this.zone = Objects.requireNonNull(zone, "Zone can not be null");
		
		this.pathFilter = new DynamicPathPredicate();

		// We dont tick. We are passiv.
		setEnabled(false);
	}

	@Override
	protected void initialize() {
		super.initialize();
		
		// Hook up the dynamic path filter to the zone.
		ctx.getMessageProvider().subscribe(pathFilter, zone);

		entityFactory = new EcsEntityFactory(world, ctx);
		accountRegistry = ctx.getAccountRegistry();
		activePlayerSubscription = world.getAspectSubscriptionManager().get(Aspect.all(Visible.class, Active.class));
	}

	/**
	 * A new player bestia was added to this zone. Subscribe for input messages
	 * directed to this bestia.
	 */
	@Override
	protected void inserted(int entityId) {
		final PlayerEntityProxy pbm = playerMapper.get(entityId).playerBestia;
		final int playerBestiaId = pbm.getPlayerBestiaId();
		final Long accountId = pbm.getAccountId();

		bestiaEntityRegister.put(playerBestiaId, entityId);
		accountRegistry.incrementBestiaOnline(accountId);

		// Add the entity to the register so it can be deleted.
		if (!accountBestiaRegister.containsKey(accountId)) {
			accountBestiaRegister.put(accountId, new HashSet<>());
		}
		accountBestiaRegister.get(accountId).add(entityId);
		
		// Register the zone now to receive messages from this account.
		pathFilter.subscribe(InputMessage.getInputMessagePath(accountId, playerBestiaId));
	}

	/**
	 * If a player bestia was removed from the ECS we want to unsubscribe from
	 * the messages for this particular bestia.
	 */
	@Override
	protected void removed(int entityId) {
		final PlayerEntityProxy pbm = playerMapper.get(entityId).playerBestia;
		final int playerBestiaId = pbm.getPlayerBestiaId();
		final Long accountId = pbm.getAccountId();

		bestiaEntityRegister.remove(playerBestiaId);
		accountRegistry.decrementBestiaOnline(accountId);

		accountBestiaRegister.get(accountId).remove(entityId);
		if (accountBestiaRegister.get(accountId).size() == 0) {
			accountBestiaRegister.remove(accountId);
		}
		
		// Un-register the zone now to receive messages from this account.
		pathFilter.unsubscribe(InputMessage.getInputMessagePath(accountId, playerBestiaId));
	}

	@Override
	protected void processSystem() {
		// no op (disabled)
	}

	/**
	 * Spawns a player bestia.
	 * 
	 * @param pb
	 */
	public void spawnBestia(net.bestia.model.domain.PlayerBestia pb) {
		
		final PlayerEntityBuilder eb = new PlayerEntityBuilder(pb);

		entityFactory.spawn(eb);
	}

	/**
	 * Sends the given message to all active bestias in sight.
	 * 
	 * @param source
	 *            The entity from which the sight range will be calculated.
	 * @param msg
	 */
	public void sendMessageToSightrange(int source, AccountMessage msg) {
		if (msg == null) {
			throw new IllegalArgumentException("Msg can not be null.");
		}

		final Position sourcePosition = positionMapper.getSafe(source);

		if (sourcePosition == null) {
			LOG.warn("Entity has no position. Can not send messages.");
			return;
		}

		final IntBag receivers = getActivePlayersInSight(sourcePosition);

		LOG.trace("Sending msg: {} to players: {}", msg.toString(), receivers.toString());

		for (int i = 0; i < receivers.size(); i++) {
			final int receiverId = receivers.get(i);

			final PlayerBestia pb = playerBestiaMapper.get(receiverId);
			final long accId = pb.playerBestia.getAccountId();
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
	public PlayerEntityProxy getPlayerBestiaProxy(int playerBestiaId) {
		final int entityId = getEntityIdFromBestia(playerBestiaId);
		final Entity entity = world.getEntity(entityId);

		if (entity == null) {
			// Should not happen i guess.
			return null;
		}

		return playerBestiaMapper.get(entity).playerBestia;
	}

	/**
	 * Removes all bestia for a given account.
	 * 
	 * @param accountId
	 */
	public void despawnAccountBestias(long accountId) {
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
