package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.entity.EntityService;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

/**
 * This command can create on-the-fly entities which is important for testing.
 * 
 * @author Thomas Felix
 *
 */
public class CreateCommand extends BaseChatCommand {

	private final static String CMD_MATCH = "^/create ";

	//private final EntityService entityService;
	
	@Autowired
	public CreateCommand(
			AccountDAO accDao, 
			ZoneAkkaApi akkaApi) {
		super(accDao, akkaApi);
		
		//this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	public boolean isCommand(String text) {
		return text.matches(CMD_MATCH);
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}

	@Override
	protected void executeCommand(Account account, String text) {
		
		

	}

}
