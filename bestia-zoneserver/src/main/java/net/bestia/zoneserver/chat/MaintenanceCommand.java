package net.bestia.zoneserver.chat;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.MaintenanceLevel;
import net.bestia.zoneserver.configuration.RuntimeConfigurationService;
import net.bestia.zoneserver.service.LoginService;

/**
 * Allows admins to set the server into maintenance mode. Use with caution since
 * this will disconnect ALL users! It can only switch the server into partial
 * maintenance since full maintainance would also disconnect the admin itself.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class MaintenanceCommand extends BaseChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MaintenanceCommand.class);

	private static final String CMD_START_REGEX = "^/maintenance .*";
	private static final Pattern CMD_PATTERN = Pattern.compile("/maintenance (.+)");

	private final RuntimeConfigurationService config;
	private final LoginService loginService;


	@Autowired
	public MaintenanceCommand(
			AccountDAO accDao, 
			ZoneAkkaApi akkaApi, 
			LoginService loginService, 
			RuntimeConfigurationService config) {
		super(accDao, akkaApi);

		this.loginService = Objects.requireNonNull(loginService);
		this.config = Objects.requireNonNull(config);
	}

	@Override
	public boolean isCommand(String text) {
		return text.matches(CMD_START_REGEX);
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}

	@Override
	protected void executeCommand(Account account, String text) {

		// Extract name of the new map.
		final Matcher match = CMD_PATTERN.matcher(text);

		if (!match.find()) {
			printError(account.getId());
			return;
		}

		boolean isMaintenance;

		try {
			final String txt = match.group(1);
			isMaintenance = Boolean.parseBoolean(txt);
		} catch (Exception e) {
			printError(account.getId());
			return;
		}
		
		LOG.info("Account {} set maintenance to: {}", account.getId(), isMaintenance);

		if(isMaintenance) {
			sendSystemMessage(account.getId(), "Server maintenance: true");
			config.setMaintenanceMode(MaintenanceLevel.PARTIAL);
			loginService.logoutAllUsersBelow(UserLevel.SUPER_GM);
		} else {
			sendSystemMessage(account.getId(), "Server maintenance: false");
			config.setMaintenanceMode(MaintenanceLevel.NONE);
		}
	}

	private void printError(long accId) {
		sendSystemMessage(accId, "Usage: /maintenance [true, false]");
	}
}
