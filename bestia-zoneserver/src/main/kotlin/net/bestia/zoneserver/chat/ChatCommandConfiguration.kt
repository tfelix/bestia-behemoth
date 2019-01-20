package net.bestia.zoneserver.chat

import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.chat.ChatCommandService
import net.bestia.zoneserver.chat.MetaChatCommand
import net.bestia.zoneserver.chat.MobSpawnModule
import net.bestia.zoneserver.chat.SubCommandModule
import net.bestia.zoneserver.entity.factory.EntityFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Assembles the meta chat commands so they are getting picked up by the
 * [ChatCommandService].
 *
 * @author Thomas Felix
 */
@Configuration
class ChatCommandConfiguration {

  @Bean
  internal fun getSpawnChatCommand(akkaApi: MessageApi, entityFactory: EntityFactory): MetaChatCommand {
    val spawnCmd = MetaChatCommand("/spawn")
    val mobModule = MobSpawnModule(akkaApi, entityFactory)
    spawnCmd.addCommandModule(mobModule)

    return spawnCmd
  }
}
