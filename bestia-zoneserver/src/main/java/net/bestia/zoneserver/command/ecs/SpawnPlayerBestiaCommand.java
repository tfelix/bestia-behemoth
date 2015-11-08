package net.bestia.zoneserver.command.ecs;

import java.util.Set;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.messages.ZoneWrapperMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;

public class SpawnPlayerBestiaCommand extends ECSCommand {

	private PlayerBestiaSpawnManager spawnManager;

	/**
	 * Make sure we are receiving only wrapped messages.
	 */
	@Override
	public String handlesMessageId() {
		return ZoneWrapperMessage.getWrappedMessageId(LoginBroadcastMessage.MESSAGE_ID);
	}

	@Override
	protected void initialize() {
		super.initialize();

		spawnManager = world.getSystem(PlayerBestiaSpawnManager.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		// Find WHICH of the account bestias which are located on this zone.
		final PlayerBestiaDAO bestiaDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);
		AccountDAO accountDao = ctx.getServiceLocator().getBean(AccountDAO.class);

		Account account = accountDao.find(message.getAccountId());
		final Set<PlayerBestia> bestias = bestiaDao.findPlayerBestiasForAccount(message.getAccountId());

		// Add master as well since its not listed as a "player bestia".
		bestias.add(account.getMaster());

		for (PlayerBestia playerBestia : bestias) {
			final String mapname = playerBestia.getCurrentPosition().getMapDbName();
			final boolean isOnZone = mapname.equals(zone.getName());
			
			if (isOnZone) {
				spawnManager.spawnBestia(playerBestia);
			}
		}		
	}
}
