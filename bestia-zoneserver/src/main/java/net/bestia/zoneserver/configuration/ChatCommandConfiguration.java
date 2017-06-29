package net.bestia.zoneserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bestia.model.dao.AccountDAO;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.chat.MetaChatCommand;
import net.bestia.zoneserver.chat.SetScriptModule;

/**
 * Prepares the meta chat commands.
 * 
 * @author Thomas Felix
 *
 */
@Configuration
public class ChatCommandConfiguration {
	
	@Bean
	public MetaChatCommand getSetChatCommand(AccountDAO accDao, ZoneAkkaApi akkaApi) {
		
		final MetaChatCommand setCmd = new MetaChatCommand("/entity");
		
		SetScriptModule scriptModule = new SetScriptModule(accDao, akkaApi);
		
		setCmd.addCommandModule(scriptModule);
		
		return setCmd;
		
	}

}
