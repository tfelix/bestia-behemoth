package net.bestia.zoneserver.ecs.command;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.utils.EntityBuilder;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.InputWrapperMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Chat;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.system.ChatSystem;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Spawns an Chat entity in the system which will by the {@link ChatSystem} be transmitted to all active player bestias
 * in sight.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PublicChatCommand extends ECSCommand {

	private ComponentMapper<PlayerBestia> playerMapper;

	@Override
	public String handlesMessageId() {
		return ChatMessage.MESSAGE_ID;
	}

	@Override
	protected void initialize() {
		playerMapper = world.getMapper(PlayerBestia.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		// Unwrap the message.
		@SuppressWarnings("unchecked")
		ChatMessage msg = ((InputWrapperMessage<ChatMessage>) message).getMessage();

		final Entity chatEntity = new EntityBuilder(world).build();

		final EntityEdit chatEdit = chatEntity.edit();

		final Chat chat = chatEdit.create(Chat.class);
		final Position position = chatEdit.create(Position.class);

		final Location loc = playerMapper.get(player).playerBestiaManager.getLocation();

		chat.chatMessage = msg;
		position.position = new Vector2(loc.getX(), loc.getY());
	}

}
