package net.bestia.zoneserver.ecs.manager;

import java.util.Queue;
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

import net.bestia.messages.Message;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.zoneserver.command.CommandContext;
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
import net.bestia.zoneserver.routing.MessageFilter;
import net.bestia.zoneserver.routing.MessageIDFilter;
import net.bestia.zoneserver.routing.MessageProcessor;
import net.bestia.zoneserver.zone.shape.Vector2;

@Wire
public class PlayerBestiaInstanceManager extends BaseEntitySystem implements MessageProcessor {

	private final static Logger LOG = LogManager.getLogger(PlayerBestiaInstanceManager.class);

	@Wire
	private CommandContext ctx;

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

	private final MessageFilter spawnMessageFilter = new MessageIDFilter(SpawnPlayerBestiaMessage.MESSAGE_ID);

	private final DynamicMessageFilter messageFilter = new DynamicMessageFilter();
	private final Queue<SpawnPlayerBestiaMessage> msgQueue = new ConcurrentLinkedQueue<>();
	private Archetype playerBestiaArchetype;

	public PlayerBestiaInstanceManager(MessageProcessor zone) {
		super(Aspect.all(PlayerBestia.class));

		this.processor = zone;
	}

	@Override
	protected void initialize() {
		super.initialize();

		// Prepare the message filter.
		ctx.getServer().getMessageRouter().registerFilter(messageFilter, processor);
		ctx.getServer().getMessageRouter().registerFilter(spawnMessageFilter, this);

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
	}

	/**
	 * If a player bestia was removed from the ECS we want to unsubscribe from
	 * the messages for this particular bestia.
	 */
	@Override
	protected void removed(int entityId) {
		final int playerBestiaId = playerMapper.get(entityId).playerBestiaManager.getPlayerBestiaId();
		messageFilter.removeId(playerBestiaId);
	}

	@Override
	protected boolean checkProcessing() {
		return !msgQueue.isEmpty();
	}

	@Override
	protected void processSystem() {

		final PlayerBestiaDAO pbDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);

		while (msgQueue.peek() != null) {
			// We spawn the bestia.
			final SpawnPlayerBestiaMessage msg = msgQueue.poll();

			final net.bestia.model.domain.PlayerBestia pb = pbDao.find(msg.getPlayerBestiaId());

			final Entity pbEntity = world.createEntity(playerBestiaArchetype);

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

			// Now set all the needed values.
			LOG.trace("Spawning bestia.");
		}
	}

	@Override
	public void processMessage(Message msg) {

		if (!(msg instanceof SpawnPlayerBestiaMessage)) {
			return;
		}

		msgQueue.add((SpawnPlayerBestiaMessage) msg);
	}

}
