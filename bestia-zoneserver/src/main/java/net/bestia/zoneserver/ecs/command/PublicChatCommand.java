package net.bestia.zoneserver.ecs.command;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.utils.EntityBuilder;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.Message;
import net.bestia.messages.PublicChatMessage;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Chat;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;

public class PublicChatCommand extends ECSCommand {
	
	private ComponentMapper<PlayerBestia> playerMapper;

	@Override
	public String handlesMessageId() {
		return PublicChatMessage.MESSAGE_ID;
	}
	
	@Override
	protected void initialize() {
		playerMapper = world.getMapper(PlayerBestia.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		// Unwrap the message.
		ChatMessage msg = ((PublicChatMessage) message).getChatMessage();
		
		final Entity chatEntity = new EntityBuilder(world).build();
		
		final EntityEdit chatEdit = chatEntity.edit();
		
		final Chat chat = chatEdit.create(Chat.class);
		final Position position = chatEdit.create(Position.class);
		
		final Location loc = playerMapper.get(player).playerBestiaManager.getLocation();
		
		chat.chatMessage = msg;
		position.x = loc.getX();
		position.y = loc.getY();
	}

}
