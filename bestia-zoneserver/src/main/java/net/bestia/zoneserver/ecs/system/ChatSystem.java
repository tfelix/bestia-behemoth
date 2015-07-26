package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.ChatMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.manager.NetworkManager;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

@Wire
public class ChatSystem extends EntityProcessingSystem {
	
	private ComponentMapper<Active> activeMapper;
	private ComponentMapper<PlayerControlled> playerMapper;
	
	private NetworkManager networkManager;
	
	@Wire
	private CommandContext ctx;

	@SuppressWarnings("unchecked")
	public ChatSystem() {
		super(Aspect.all(Active.class, PlayerControlled.class));
		// no op.
	}

	@Override
	protected void process(Entity e) {
		
		final Active active = activeMapper.get(e);
		
		while(!active.chatQueue.isEmpty()) {
			final ChatMessage msg = active.chatQueue.poll();
			
			// Get all bestias in range.
			final IntBag receiverEntities = subscription.getEntities();
			
			for(int i = 0; i < receiverEntities.size(); i++) {
				final int entityId = receiverEntities.get(i);
				Entity receiverEntity = world.getEntity(entityId);
				
				if(!networkManager.isInSightDistance(e, receiverEntity)) {
					continue;
				}
				
				final PlayerControlled playerCtrl = playerMapper.get(receiverEntity);
				final long receiverAccId = playerCtrl.playerBestia.getBestia().getOwner().getId();
				
				final ChatMessage forwardMsg = ChatMessage.getForwardMessage(receiverAccId, msg);
				ctx.getServer().sendMessage(forwardMsg);
			}
		}
	}

}
