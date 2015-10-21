package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.ChatMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Chat;
import net.bestia.zoneserver.ecs.component.PlayerBestia;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

@Wire
public class ChatSystem extends EntityProcessingSystem {

	private ComponentMapper<Chat> chatMapper;
	private ComponentMapper<PlayerBestia> playerMapper;

	private EntitySubscription activePlayerEntities;

	@Wire
	private CommandContext ctx;

	public ChatSystem() {
		super(Aspect.all(Chat.class));
		// no op.
	}

	@Override
	protected void initialize() {
		final AspectSubscriptionManager asm = world.getSystem(AspectSubscriptionManager.class);
		activePlayerEntities = asm.get(Aspect.all(Active.class, PlayerBestia.class));
	}

	@Override
	protected void process(Entity e) {

		// All active bestias on this zone.
		final IntBag entityIds = activePlayerEntities.getEntities();

		final ChatMessage chatMsg = chatMapper.get(e).chatMessage;
		
		// Remove the chat msg entity.
		e.edit().deleteEntity();

		for (int i = 0; i < entityIds.size(); i++) {
			final Entity receiverEntity = world.getEntity(entityIds.get(i));
			
			// TODO Are they in sight range?
			
			final PlayerBestia player = playerMapper.get(receiverEntity);
			final long receiverAccId = player.playerBestiaManager.getAccountId();

			// Skip the same owner of the bestia.
			if (chatMsg.getAccountId() == receiverAccId) {
				continue;
			}

			final ChatMessage forwardMsg = ChatMessage.getForwardMessage(receiverAccId, chatMsg);
			ctx.getServer().sendMessage(forwardMsg);
		}
	}

}
