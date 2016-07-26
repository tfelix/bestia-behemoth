package net.bestia.zoneserver.ecs.system;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import net.bestia.messages.entity.EntityAction;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.EntityUpdateMessageFactory;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;

/**
 * As soon as an active player bestia is spawned into the world it gets updated
 * with all entities in its sight.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire(injectInherited = true)
public class ActiveSpawnUpdateSystem extends BaseEntitySystem {

	public ActiveSpawnUpdateSystem() {
		super(Aspect.all(Active.class, PlayerBestia.class));
		setEnabled(false);
	}

	private static final Logger LOG = LogManager.getLogger(ActiveSpawnUpdateSystem.class);

	private AspectSubscriptionManager asm;

	@Wire
	private CommandContext ctx;
	private EntitySubscription visibleSubscription;
	private EntityUpdateMessageFactory updateMessageFactory;
	private ComponentMapper<PlayerBestia> playerMapper;

	@Override
	protected void initialize() {
		super.initialize();

		visibleSubscription = asm.get(Aspect.all(Visible.class));

		updateMessageFactory = new EntityUpdateMessageFactory(getWorld());
	}

	@Override
	protected void inserted(int entityId) {
		// TODO Get the Visible Entities in range of this new player. Currently
		// send all.
		final IntBag visibleEntities = visibleSubscription.getEntities();
		final long accId = playerMapper.get(entityId).playerBestia.getAccountId();

		LOG.trace("New active player, sending {} entities.", visibleEntities.size());

		final List<EntityUpdateMessage> msgs = updateMessageFactory.createMessages(visibleEntities);
		
		LOG.trace("Sending update for entities: {} to accId: {}", msgs.size(), accId);

		msgs.stream().forEach(x -> {
			x.setAccountId(accId);
			x.setAction(EntityAction.APPEAR);
			ctx.getServer().sendMessage(x);
		});
	}

	@Override
	protected void processSystem() {
		// no op. Disabled.
	}
}
