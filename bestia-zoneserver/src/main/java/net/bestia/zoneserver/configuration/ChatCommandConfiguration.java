package net.bestia.zoneserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bestia.entity.MobFactory;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
import net.bestia.zoneserver.chat.ChatCommandService;
import net.bestia.zoneserver.chat.MetaChatCommand;
import net.bestia.zoneserver.chat.MobSpawnModule;

/**
 * Assembles the meta chat commands so they are getting picked up by the
 * {@link ChatCommandService}.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class ChatCommandConfiguration {

	@Bean
	public MetaChatCommand getSetChatCommand() {

		final MetaChatCommand setCmd = new MetaChatCommand("/entity");

		return setCmd;
	}

	@Bean
	public MetaChatCommand getSpawnChatCommand(ZoneAkkaApi akkaApi, MobFactory mobFactory) {

		final MetaChatCommand spawnCmd = new MetaChatCommand("/spawn");

		final MobSpawnModule mobModule = new MobSpawnModule(akkaApi, mobFactory);

		spawnCmd.addCommandModule(mobModule);

		return spawnCmd;
	}

}
