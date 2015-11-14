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
import net.bestia.zoneserver.command.chat.ChatCommandExecutor;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;

public class ChatCommand extends ECSCommand {

	private ChatCommandExecutor chatCommandExecutor;
	private ComponentMapper<PlayerBestia> playerMapper;
	private EntitySubscription activePlayerEntities;

	@Override
	protected void initialize() {
		super.initialize();

		chatCommandExecutor = new ChatCommandExecutor();

		playerMapper = world.getMapper(PlayerBestia.class);
		final AspectSubscriptionManager asm = world.getSystem(AspectSubscriptionManager.class);
		activePlayerEntities = asm.get(Aspect.all(Active.class, PlayerBestia.class));
	}

	@Override
	public String handlesMessageId() {
		return InputWrapperMessage.getWrappedMessageId(ChatMessage.MESSAGE_ID);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		@SuppressWarnings("unchecked")
		final InputWrapperMessage<ChatMessage> wrappedMsg = (InputWrapperMessage<ChatMessage>) message;
		final ChatMessage msg = wrappedMsg.getMessage();

		switch (msg.getChatMode()) {
		case COMMAND:
			chatCommandExecutor.execute(msg, getPlayerBestiaManager(), ctx);
			break;
		case PUBLIC:
			// Send the message to all active player in the range so send to the
			// ecs since we must make sight tests.
			handlePublicChat(msg, ctx);
			break;
		default:
			// no op.
			break;
		}
	}

	private void handlePublicChat(ChatMessage msg, CommandContext ctx) {
		// All active bestias on this zone.
		final IntBag entityIds = activePlayerEntities.getEntities();
		final int senderPlayerBestiaId = getPlayerBestiaManager().getPlayerBestiaId();

		for (int i = 0; i < entityIds.size(); i++) {
			final Entity receiverEntity = world.getEntity(entityIds.get(i));

			// TODO Are they in sight range?

			final PlayerBestia player = playerMapper.get(receiverEntity);
			
			final long receiverAccId = player.playerBestiaManager.getAccountId();

			// Skip the same owner of the bestia.
			if (msg.getAccountId() == receiverAccId) {
				continue;
			}

			final ChatMessage forwardMsg = ChatMessage.getEchoMessage(receiverAccId, msg);
			forwardMsg.setPlayerBestiaId(senderPlayerBestiaId);
			ctx.getServer().sendMessage(forwardMsg);
		}
	}
}
