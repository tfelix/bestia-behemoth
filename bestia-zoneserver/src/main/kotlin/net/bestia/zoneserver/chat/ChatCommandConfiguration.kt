package net.bestia.zoneserver.chat

import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.factory.ItemFactory
import net.bestia.zoneserver.entity.factory.MobFactory
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
  internal fun getSpawnChatCommand(
      msgApi: MessageApi,
      mobFactory: MobFactory,
      itemFactory: ItemFactory
  ): MetaChatCommand {
    val spawnCmd = MetaChatCommand("/spawn")
    val mobModule = SpawnMobModule(msgApi, mobFactory)
    val itemModule = SpawnItemModule(msgApi, itemFactory)
    spawnCmd.addCommandModule(mobModule)
    spawnCmd.addCommandModule(itemModule)

    return spawnCmd
  }
}
