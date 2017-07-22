package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
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
	public ServerVersionChatCommand(AccountDAO accDao, ZoneAkkaApi akkaApi, StaticConfigService config) {
		super(accDao, akkaApi);

		this.config = Objects.requireNonNull(config);
	}

	@Override
	public boolean isCommand(String text) {
		return text.equals("/server");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.USER;
	}

	@Override
	protected void executeCommand(Account account, String text) {
		final String replyText = String.format("Bestia Behemoth Server (%s)", config.getServerVersion());
		sendSystemMessage(account.getId(), replyText);
	}

}
