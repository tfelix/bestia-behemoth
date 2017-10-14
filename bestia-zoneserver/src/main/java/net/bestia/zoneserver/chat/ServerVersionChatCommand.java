package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.messages.MessageApi;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.configuration.StaticConfigService;

/**
 * Sends the server version to the client.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ServerVersionChatCommand extends BaseChatCommand {

	private final StaticConfigService config;

	@Autowired
	public ServerVersionChatCommand(MessageApi akkaApi, StaticConfigService config) {
		super(akkaApi);

		this.config = Objects.requireNonNull(config);
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/server ") || text.equals("/server");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.USER;
	}

	@Override
	public void executeCommand(Account account, String text) {
		final String replyText = String.format("Bestia Behemoth Server (%s)", config.getServerVersion());
		sendSystemMessage(account.getId(), replyText);
	}

	@Override
	protected String getHelpText() {
		return "Usage: /server";
	}

}
