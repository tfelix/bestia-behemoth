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
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * Moves the player to the given map coordinates if he has GM permissions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class MapMoveCommand implements ChatCommand {
	
	private static final Logger LOG = LoggerFactory.getLogger(MapMoveCommand.class);
	private final static Pattern cmdPattern = Pattern.compile("/mm (\\d+) (\\d+)");
	
	private final AccountDAO accDao;
	private final PlayerEntityService playerBestiaService;
	
	
	@Autowired
	public MapMoveCommand(AccountDAO accDao, PlayerEntityService pbService) {
		
		this.accDao = Objects.requireNonNull(accDao);
		this.playerBestiaService = Objects.requireNonNull(pbService);
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
		final Matcher match = cmdPattern.matcher(text);
		
		if(!match.find()) {
			LOG.debug("Wrong command usage: {}", text);
			return;
		}
		
		final long x = Long.parseLong(match.group(1));
		final long y = Long.parseLong(match.group(2));
		
		// TODO Safety checks.
		
		final PlayerBestiaEntity pbe = playerBestiaService.getActivePlayerEntity(accId);
		pbe.setPosition(x, y);
		playerBestiaService.putPlayerEntity(pbe);		
	}

}
