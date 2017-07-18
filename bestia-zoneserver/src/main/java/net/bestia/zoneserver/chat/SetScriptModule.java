package net.bestia.zoneserver.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

public class SetScriptModule extends BaseChatCommand {
	
	private static final Logger LOG = LoggerFactory.getLogger(SetScriptModule.class);
	
	private static final String CMD_STR = "set ";

	public SetScriptModule(AccountDAO accDao, ZoneAkkaApi akkaApi) {
		super(accDao, akkaApi);
		
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith(CMD_STR);
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}

	@Override
	protected void executeCommand(Account account, String text) {
		
		LOG.info("set script executed.");
		
	}

}
