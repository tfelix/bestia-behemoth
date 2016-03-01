package net.bestia.zoneserver.command.ecs;

import java.util.Set;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.messages.ZoneMessageDecorator;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.AccountService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;

public class LoginBroadcastCommand extends ECSCommand {

	private PlayerBestiaSpawnManager spawnManager;

	/**
	 * Make sure we are receiving only wrapped messages.
	 */
	@Override
	public String handlesMessageId() {
		return ZoneMessageDecorator.getWrappedMessageId(LoginBroadcastMessage.MESSAGE_ID);
	}

	@Override
	protected void initialize() {
		super.initialize();

		spawnManager = world.getSystem(PlayerBestiaSpawnManager.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		final AccountService accService = ctx.getServiceLocator().getBean(AccountService.class);
		final Set<PlayerBestia> bestias = accService.getAllBestias(message.getAccountId());

		for (PlayerBestia playerBestia : bestias) {
			final String mapname = playerBestia.getCurrentPosition().getMapDbName();
			final boolean isOnZone = mapname.equals(zone.getName());
			
			if (isOnZone) {
				spawnManager.spawnBestia(playerBestia);
			}
		}		
	}
	
	@Override
	public String toString() {
		return "SpawnPlayerBestiaCommand[]";
	}
}
