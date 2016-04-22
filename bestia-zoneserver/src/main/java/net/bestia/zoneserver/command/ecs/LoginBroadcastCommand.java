package net.bestia.zoneserver.command.ecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
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
		return LoginBroadcastMessage.MESSAGE_ID;
	}

	@Override
	protected void initialize() {
		super.initialize();

		spawnManager = world.getSystem(PlayerBestiaSpawnManager.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		final LoginBroadcastMessage lbMsg = (LoginBroadcastMessage) message;
		
		final AccountService accService = ctx.getServiceLocator().getBean(AccountService.class);
		final Set<PlayerBestia> bestias = accService.getAllBestias(lbMsg.getAccountId());
		
		final List<PlayerBestia> zoneBestias = new ArrayList<>();

		for (PlayerBestia playerBestia : bestias) {
			final String mapname = playerBestia.getCurrentPosition().getMapDbName();

			if (mapname.equals(zone.getName())) {
				zoneBestias.add(playerBestia);
			}
		}
		
		// No bestias on this zone do nothing.
		if(zoneBestias.size() == 0) {
			return;
		}
		
		ctx.getAccountRegistry().registerLogin(lbMsg.getAccountId(), lbMsg.getToken());
		zoneBestias.stream().forEach(pb -> spawnManager.spawnBestia(pb));
	}
	
	@Override
	public String toString() {
		return "SpawnPlayerBestiaCommand[]";
	}
}
