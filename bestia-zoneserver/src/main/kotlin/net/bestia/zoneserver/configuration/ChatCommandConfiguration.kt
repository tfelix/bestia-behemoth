package net.bestia.zoneserver.configuration

import net.bestia.entity.factory.MobFactory
import net.bestia.zoneserver.chat.ChatCommandService
import net.bestia.zoneserver.chat.MetaChatCommand
import net.bestia.zoneserver.chat.MobSpawnModule
import org.springframework.context.annotation.Configuration

/**
 * Assembles the meta chat commands so they are getting picked up by the
 * [ChatCommandService].
 *
 * @author Thomas Felix
 */
@Configuration
class ChatCommandConfiguration {

  // @Bean This currently gives a strange exception
  fun getSpawnChatCommand(akkaApi: MessageApi, mobFactory: MobFactory): MetaChatCommand {
    val spawnCmd = MetaChatCommand("/spawn")
    val mobModule = MobSpawnModule(akkaApi, mobFactory)
    spawnCmd.addCommandModule(mobModule)

    return spawnCmd
  }
}
