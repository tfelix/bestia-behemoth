package net.bestia.zoneserver.command.chat;

import org.junit.Test;
import org.mockito.Mockito;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.ChatMessage.Mode;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class AddItemChatUserCommandTest {

	// TODO Richtige Umsetzung des Command Context machen.
	//@Test
	public void execute_unknownItem_msg() {
		final AddItemChatUserCommand cmd = new AddItemChatUserCommand();

		final ChatMessage msg = getMessage();
		msg.setText("/item blablablub");

		final CommandContext ctx = getMockedContext();
		final PlayerBestiaManager player = getMockedPlayerManager();

		cmd.execute(msg, player, ctx);
	}

	public void execute_numeric_ok() {

	}

	public void execute_numericWithNumber_ok() {

	}

	// TODO Richtige Umsetzung von CommadnContext machen.
	//@Test
	public void execute_dbName_ok() {
		final AddItemChatUserCommand cmd = new AddItemChatUserCommand();

		final ChatMessage msg = getMessage();
		msg.setText("/item apple 10");

		final CommandContext ctx = getMockedContext();
		final PlayerBestiaManager player = getMockedPlayerManager();

		cmd.execute(msg, player, ctx);
		
		
	}

	public void execute_dbNameWithNumber_ok() {

	}

	public void execute_wrondCmd_silentFail() {
		final AddItemChatUserCommand cmd = new AddItemChatUserCommand();

		final ChatMessage msg = getMessage();
		msg.setText("/sdfjkh");

		final CommandContext ctx = getMockedContext();
		final PlayerBestiaManager player = getMockedPlayerManager();

		cmd.execute(msg, player, ctx);
	}

	public ChatMessage getMessage() {
		final ChatMessage msg = new ChatMessage();

		msg.setAccountId(1);
		msg.setChatMessageId(123);
		msg.setChatMode(Mode.COMMAND);
		msg.setPlayerBestiaId(1);

		return msg;
	}

	public CommandContext getMockedContext() {
		final CommandContext ctx = Mockito.mock(CommandContext.class);

		return ctx;
	}

	public PlayerBestiaManager getMockedPlayerManager() {
		final PlayerBestiaManager manager = Mockito.mock(PlayerBestiaManager.class);

		return manager;
	}
}
