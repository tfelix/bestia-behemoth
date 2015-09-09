package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

/**
 * This system is responsible to keep track of appearing, disappearing and changing visual entities. Changes in such
 * entities must be reported to all player controlled and active entities near by.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class VisibleNetworkUpdateSystem extends NetworkUpdateSystem {

	//private static final Logger log = LogManager.getLogger(VisibleNetworkUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private EntitySubscription playerSubscription;

	@SuppressWarnings("unchecked")
	public VisibleNetworkUpdateSystem() {
		super(Aspect.all(Visible.class, Changed.class));

	}

	@SuppressWarnings("unchecked")
	@Override
	protected void initialize() {
		super.initialize();

		// Workaround must be set since parent gets no wireing.
		setCommandContext(ctx);

		final AspectSubscriptionManager asm = world.getManager(AspectSubscriptionManager.class);
		playerSubscription = asm.get(Aspect.all(PlayerBestia.class, Active.class));
	}

	@Override
	protected void inserted(Entity e) {
		super.inserted(e);

		final IntBag playersBag = playerSubscription.getEntities();

		for (int i = 0; i < playersBag.size(); i++) {
			final int id = playersBag.get(i);
			final Entity playerEntity = world.getEntity(id);

			// Is this player in sight of entity? If not continue.
			if (!isInSightDistance(playerEntity, e)) {
				continue;
			}

			sendUpdate(playerEntity, e, EntityAction.APPEAR);
		}

	}

	@Override
	protected void process(Entity e) {
		// Find all players.
		final IntBag playersBag = playerSubscription.getEntities();

		for (int i = 0; i < playersBag.size(); i++) {
			final int id = playersBag.get(i);
			final Entity playerEntity = world.getEntity(id);

			// Is this player in sight of entity? If not continue.
			if (!isInSightDistance(playerEntity, e)) {
				continue;
			}

			sendUpdate(playerEntity, e, EntityAction.UPDATE);
		}

		e.edit().remove(Changed.class);
	}

}
