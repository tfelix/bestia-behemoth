package net.bestia.zoneserver.messaging.preprocess;

import java.util.Set;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.command.CommandContext;

/**
 * Check if this server is responsible for at least one login.
 * 
 * @author Thomas
 *
 */
public class LoginMessagePreprocessor extends MessagePreprocessor {

	public LoginMessagePreprocessor(CommandContext ctx) {
		super(ctx);
		// no op.
	}

	@Override
	public Message process(Message message) {
		if (!(message instanceof LoginBroadcastMessage)) {
			return message;
		}

		// gather bestias.
		PlayerBestiaDAO  bestiaDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);
		AccountDAO accountDao = ctx.getServiceLocator().getBean(AccountDAO.class);

		Account account = accountDao.find(message.getAccountId());
		Set<PlayerBestia> bestias = bestiaDao.findPlayerBestiasForAccount(message.getAccountId());

		// Add master as well since its not listed as a "player bestia".
		bestias.add(account.getMaster());
		
		boolean atLeastOne = false;
		for (PlayerBestia playerBestia : bestias) {
			if (isBestiaOnZone(playerBestia)) {
				atLeastOne = true;
				break;
			}
		}
		
		if(atLeastOne) {
			return message;
		} else {
			return null;
		}

	}
	
	private boolean isBestiaOnZone(PlayerBestia playerBestia) {
		final Zoneserver server = ctx.getServer();
		final Set<String> zones = server.getResponsibleZones();
		return zones.contains(playerBestia.getCurrentPosition().getMapDbName());
	}

}
