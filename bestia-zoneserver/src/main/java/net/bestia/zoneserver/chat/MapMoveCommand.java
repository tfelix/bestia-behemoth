package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;

/**
 * Moves the player to the given map coordinates if he has GM permissions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class MapMoveCommand implements ChatCommand {
	
	private final AccountDAO accDao;
	
	@Autowired
	public MapMoveCommand(AccountDAO accDao) {
		
		this.accDao = Objects.requireNonNull(accDao);
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/mm");
	}

	@Override
	public void executeCommand(long accId, String text) {
		
		final Account acc = accDao.findOne(accId);
		if(acc == null) {
			return;
		}
		
		if(acc.getUserLevel().compareTo(UserLevel.GM) < 0) {
			return;
		}
		
		// Its okay, now execute the command.
		// TODO Parse text.
		
		
		
	}

}
