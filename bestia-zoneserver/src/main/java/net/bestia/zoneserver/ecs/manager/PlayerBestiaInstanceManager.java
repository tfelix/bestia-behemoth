package net.bestia.zoneserver.ecs.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;

import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.PlayerBestiaDAO;
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
import net.bestia.zoneserver.ecs.message.SpawnPlayerBestiaMessage;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.routing.DynamicMessageFilter;
import net.bestia.zoneserver.routing.MessageIdFilter;
import net.bestia.zoneserver.routing.MessageProcessor;
import net.bestia.zoneserver.routing.ServerSubscriptionManager;
import net.bestia.zoneserver.zone.shape.Vector2;

@Wire
public class PlayerBestiaInstanceManager extends BaseEntitySystem implements MessageProcessor {

	private final static Logger LOG = LogManager.getLogger(PlayerBestiaInstanceManager.class);

	@Wire
	private CommandContext ctx;
	private ServerSubscriptionManager subscriptionManager;

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
	private final MessageProcessor processor;

	private final MessageIdFilter spawnMessageFilter = new MessageIdFilter();

	private final DynamicMessageFilter messageFilter = new DynamicMessageFilter();
	private final Queue<Message> msgQueue = new ConcurrentLinkedQueue<>();
	private Archetype playerBestiaArchetype;

	/**
	 * Holds the reference between account id and bestia entity ids.
	 */
	private final Map<Long, Set<Integer>> bestiaRegister = new HashMap<>();

	public PlayerBestiaInstanceManager(MessageProcessor zone) {
		super(Aspect.all(PlayerBestia.class));

		this.processor = zone;
	}

	@Override
	protected void initialize() {
		super.initialize();

		spawnMessageFilter.addMessageId(SpawnPlayerBestiaMessage.MESSAGE_ID);
		spawnMessageFilter.addMessageId(LogoutBroadcastMessage.MESSAGE_ID);

		// Prepare the message filter.
		ctx.getServer().getMessageRouter().registerFilter(messageFilter, processor);
		ctx.getServer().getMessageRouter().registerFilter(spawnMessageFilter, this);

		subscriptionManager = ctx.getServer().getSubscriptionManager();

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
		messageFilter.subscribeId(playerBestiaId);
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
		messageFilter.removeId(playerBestiaId);
		subscriptionManager.setOffline(pbm.getAccountId());
	}

	@Override
	protected boolean checkProcessing() {
		return !msgQueue.isEmpty();
	}

	@Override
	protected void processSystem() {

		while (msgQueue.peek() != null) {
			// We spawn the bestia.
			final Message msg = msgQueue.poll();

			switch (msg.getMessageId()) {
			case SpawnPlayerBestiaMessage.MESSAGE_ID:
				spawnBestia((SpawnPlayerBestiaMessage) msg);
				break;
			case LogoutBroadcastMessage.MESSAGE_ID:
				despawnBestia((LogoutBroadcastMessage) msg);
				break;
			default:
				// no op.
				break;
			}
		}
	}

	private void spawnBestia(SpawnPlayerBestiaMessage msg) {
		final Long accId = msg.getAccountId();
		final PlayerBestiaDAO pbDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);
		final net.bestia.model.domain.PlayerBestia pb = pbDao.find(msg.getPlayerBestiaId());

		final Entity pbEntity = world.createEntity(playerBestiaArchetype);

		// Add the entity to the register so it can be deleted.
		if (!bestiaRegister.containsKey(accId)) {
			bestiaRegister.put(accId, new HashSet<>());
		}
		bestiaRegister.get(accId).add(pbEntity.getId());

		final PlayerBestiaManager pbm = new PlayerBestiaManager(pb,
				world,
				pbEntity,
				ctx.getServer(),
				ctx.getServiceLocator());

		playerBestiaMapper.get(pbEntity).playerBestiaManager = pbm;
		positionMapper.get(pbEntity).position = new Vector2(pbm.getLocation().getX(), pbm.getLocation().getY());
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
		if (master.equals(pb)) {
			pbEntity.edit().create(Active.class);
		}

		// Now set all the needed values.
		LOG.trace("Spawning player bestia: {}.", pb);
	}

	private void despawnBestia(LogoutBroadcastMessage msg) {
		final long accId = msg.getAccountId();
		final Set<Integer> entityIds = bestiaRegister.get(accId);

		// Might happen if a connection is dropped too late/message arriving too
		// late and the bestias where already all deleted. In this case do
		// nothing.
		if (entityIds == null) {
			return;
		}

		for (Integer id : entityIds) {
			final Entity entity = world.getEntity(id.intValue());
			entity.deleteFromWorld();
			LOG.trace("Despawning player bestia (entity id: {})", id);
		}

		if (bestiaRegister.get(accId).size() == 0) {
			bestiaRegister.remove(accId);
		}
	}

	@Override
	public void processMessage(Message msg) {
		msgQueue.add(msg);
	}

}
