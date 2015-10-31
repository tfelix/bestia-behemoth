package net.bestia.zoneserver.ecs.manager;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.routing.DynamicMessageFilter;
import net.bestia.zoneserver.routing.MessageProcessor;

@Wire
public class PlayerBestiaInstanceManager extends BaseEntitySystem {

	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerBestia> playerMapper;

	private final DynamicMessageFilter messageFilter = new DynamicMessageFilter();

	public PlayerBestiaInstanceManager(MessageProcessor zone) {
		super(Aspect.all(PlayerBestia.class));
		// The system can be passiv.
		setEnabled(false);

		// Prepare the message filter.
		ctx.getServer().getMessageRouter().registerFilter(messageFilter, zone);
	}

	/**
	 * A new player bestia was added to this zone. Subscribe for input messages
	 * directed to this bestia.
	 */
	@Override
	protected void inserted(int entityId) {
		final int playerBestiaId = playerMapper.get(entityId).playerBestiaManager.getPlayerBestiaId();
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
	protected void processSystem() {
		// no op.
	}

}
