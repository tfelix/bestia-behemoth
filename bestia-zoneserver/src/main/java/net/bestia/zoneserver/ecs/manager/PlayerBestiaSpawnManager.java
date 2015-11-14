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
import com.artemis.annotations.Wire;

import net.bestia.messages.BestiaInfoMessage;
import net.bestia.messages.InputMessage;
import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.HP;
import net.bestia.zoneserver.ecs.component.HPRegenerationRate;
import net.bestia.zoneserver.ecs.component.Mana;
import net.bestia.zoneserver.ecs.component.ManaRegenerationRate;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.messaging.UserRegistry;
import net.bestia.zoneserver.messaging.preprocess.MessageProcessor;
import net.bestia.zoneserver.messaging.routing.DynamicBestiaIdMessageFilter;
import net.bestia.zoneserver.messaging.routing.MessageCombineFilter;
import net.bestia.zoneserver.messaging.routing.MessageDirectDescandantFilter;
import net.bestia.zoneserver.messaging.routing.MessageIdFilter;
import net.bestia.zoneserver.messaging.routing.MessageRouter;
import net.bestia.zoneserver.zone.shape.Vector2;

@Wire
public class PlayerBestiaSpawnManager extends BaseEntitySystem {

	private final static Logger LOG = LogManager.getLogger(PlayerBestiaSpawnManager.class);

	@Wire
	private CommandContext ctx;
	private UserRegistry subscriptionManager;

	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Attacks> attacksMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<PlayerBestia> playerBestiaMapper;
	private ComponentMapper<HP> hpMapper;
	private ComponentMapper<HPRegenerationRate> hpRegenMapper;
	private ComponentMapper<Mana> manaMapper;
	private ComponentMapper<ManaRegenerationRate> manaRegenMapper;
	private ComponentMapper<Visible> visibleMapper;
	private ComponentMapper<PlayerBestia> playerMapper;

	private final MessageProcessor zoneProcessor;

	/**
	 * This filter is to let the 
	 */
	private final DynamicBestiaIdMessageFilter zoneMessageFilter = new DynamicBestiaIdMessageFilter();
	
	private Archetype playerBestiaArchetype;

	/**
	 * Holds the reference between account id and bestia entity ids.
	 */
	private final Map<Long, Set<Integer>> accountBestiaRegister = new HashMap<>();
	private final Map<Integer, Integer> bestiaEntityRegister = new HashMap<>();

	public PlayerBestiaSpawnManager(MessageProcessor zone) {
		super(Aspect.all(PlayerBestia.class));

		this.zoneProcessor = zone;
		
		// We dont tick. We are passiv.
		setEnabled(false);
	}

	@Override
	protected void initialize() {
		super.initialize();
		
		final MessageRouter router = ctx.getServer().getMessageRouter();

		// This manager needs to know about these two messages to create and
		// delete entities.
		final MessageIdFilter spawnMessageFilter = new MessageIdFilter();
		spawnMessageFilter.addMessageId(LogoutBroadcastMessage.MESSAGE_ID);

		// Prepare the message filter for the different zones. Depending on
		// active bestias on this zone messages can be re-routed to this
		// instance.
		final MessageCombineFilter combineFilter = new MessageCombineFilter();
		combineFilter.addFilter(new MessageDirectDescandantFilter(InputMessage.class));
		combineFilter.addFilter(zoneMessageFilter);
		router.registerFilter(combineFilter, zoneProcessor);

		subscriptionManager = ctx.getServer().getUserRegistry();

		playerBestiaArchetype = new ArchetypeBuilder()
				.add(Position.class)
				.add(Attacks.class)
				.add(Bestia.class)
				.add(PlayerBestia.class)
				.add(HP.class)
				.add(HPRegenerationRate.class)
				.add(Mana.class)
				.add(ManaRegenerationRate.class)
				.add(Visible.class)
				.build(world);
	}

	/**
	 * A new player bestia was added to this zone. Subscribe for input messages
	 * directed to this bestia.
	 */
	@Override
	protected void inserted(int entityId) {
		final PlayerBestiaManager pbm = playerMapper.get(entityId).playerBestiaManager;
		final int playerBestiaId = pbm.getPlayerBestiaId();
		bestiaEntityRegister.put(playerBestiaId, entityId);
		zoneMessageFilter.subscribeId(playerBestiaId);
		subscriptionManager.setOnline(pbm.getAccountId());
	}

	/**
	 * If a player bestia was removed from the ECS we want to unsubscribe from
	 * the messages for this particular bestia.
	 */
	@Override
	protected void removed(int entityId) {
		final PlayerBestiaManager pbm = playerMapper.get(entityId).playerBestiaManager;
		final int playerBestiaId = pbm.getPlayerBestiaId();
		bestiaEntityRegister.remove(playerBestiaId);
		zoneMessageFilter.removeId(playerBestiaId);
		subscriptionManager.setOffline(pbm.getAccountId());
	}

	@Override
	protected void processSystem() {


	}

	public void spawnBestia(net.bestia.model.domain.PlayerBestia pb) {
		final Long accId = pb.getOwner().getId();
		final Entity pbEntity = world.createEntity(playerBestiaArchetype);

		// Add the entity to the register so it can be deleted.
		if (!accountBestiaRegister.containsKey(accId)) {
			accountBestiaRegister.put(accId, new HashSet<>());
		}
		accountBestiaRegister.get(accId).add(pbEntity.getId());

		final PlayerBestiaManager pbm = new PlayerBestiaManager(pb,
				world,
				pbEntity,
				ctx.getServer(),
				ctx.getServiceLocator());

		playerBestiaMapper.get(pbEntity).playerBestiaManager = pbm;
		// Need to use bestia since PBM reads information only from the entity
		// position which is obviously not set yet.
		positionMapper.get(pbEntity).position = new Vector2(pb.getCurrentPosition().getX(),
				pb.getCurrentPosition().getY());
		attacksMapper.get(pbEntity).addAll(pbm.getAttackIds());
		bestiaMapper.get(pbEntity).bestiaManager = pbm;

		final HP hp = hpMapper.get(pbEntity);
		hp.currentHP = pbm.getStatusPoints().getCurrentHp();
		hp.maxHP = pbm.getStatusPoints().getMaxHp();

		final Mana mana = manaMapper.get(pbEntity);
		mana.currentMana = pbm.getStatusPoints().getCurrentMana();
		mana.maxMana = pbm.getStatusPoints().getMaxMana();

		hpRegenMapper.get(pbEntity).rate = pbm.getHpRegenerationRate();
		manaRegenMapper.get(pbEntity).rate = pbm.getManaRegenerationRate();
		visibleMapper.get(pbEntity).sprite = pbm.getPlayerBestia().getOrigin().getSprite();

		// We need to check the bestia if its the master bestia. It will get
		// marked as active initially.
		final net.bestia.model.domain.PlayerBestia master = pb.getOwner().getMaster();
		final boolean isMaster = master.equals(pb);
		if (isMaster) {
			pbEntity.edit().create(Active.class);
		}

		// Send a update to client so he can pick up the new bestia.
		final BestiaInfoMessage infoMsg = new BestiaInfoMessage();
		infoMsg.setAccountId(accId);
		// Use the updated bestia.
		infoMsg.setBestia(pbm.getPlayerBestia());
		infoMsg.setMaster(isMaster);
		ctx.getServer().sendMessage(infoMsg);

		// Now set all the needed values.
		LOG.trace("Spawning player bestia: {}.", pb);
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
	public PlayerBestiaManager getPlayerBestiaManager(int playerBestiaId) {
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
			world.delete(id);
			LOG.trace("Despawning player bestia (entity id: {})", id);
		}

		if (accountBestiaRegister.get(accountId).size() == 0) {
			accountBestiaRegister.remove(accountId);
		}
	}

}