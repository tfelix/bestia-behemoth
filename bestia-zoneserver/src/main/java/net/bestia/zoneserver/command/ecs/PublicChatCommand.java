package net.bestia.zoneserver.command.ecs;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.InputWrapperMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;

/**
 * Spawns an Chat entity in the system which will by the {@link ChatSystem} be
 * transmitted to all active player bestias in sight.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PublicChatCommand extends ECSCommand {

	private ComponentMapper<PlayerBestia> playerMapper;
	private EntitySubscription activePlayerEntities;

	@Override
	public String handlesMessageId() {
		return ChatMessage.MESSAGE_ID;
	}

	@Override
	protected void initialize() {
		playerMapper = world.getMapper(PlayerBestia.class);
		final AspectSubscriptionManager asm = world.getSystem(AspectSubscriptionManager.class);
		activePlayerEntities = asm.get(Aspect.all(Active.class, PlayerBestia.class));
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		// Unwrap the message.
		@SuppressWarnings("unchecked")
		ChatMessage msg = ((InputWrapperMessage<ChatMessage>) message).getMessage();

		//final Location loc = playerMapper.get(player).playerBestiaManager.getLocation();

		// All active bestias on this zone.
		final IntBag entityIds = activePlayerEntities.getEntities();

		for (int i = 0; i < entityIds.size(); i++) {
			final Entity receiverEntity = world.getEntity(entityIds.get(i));

			// TODO Are they in sight range?

			final PlayerBestia player = playerMapper.get(receiverEntity);
			final long receiverAccId = player.playerBestiaManager.getAccountId();

			// Skip the same owner of the bestia.
			if (msg.getAccountId() == receiverAccId) {
				continue;
			}

			final ChatMessage forwardMsg = ChatMessage.getForwardMessage(receiverAccId, msg);
			ctx.getServer().processMessage(forwardMsg);

		}
	}
}
