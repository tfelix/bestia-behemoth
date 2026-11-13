package net.bestia.zone.item.mining

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Appraising a Rough Gemstone with Gem Lore: the stone becomes the rough gem it really is. Which one is rolled
 * here, by how common each gem is, because nothing about a stone was decided when it dropped.
 */
@Component
class RoughGemScript(
  private val skills: SkillRepository,
  private val items: ItemTemplateRegistry,
  private val messages: OutMessageProcessor,
  private val random: Random = ThreadLocalRandomSource,
) : ItemScript {

  override val itemId: Long = ITEM_ID

  private val gemLoreId: Long? by lazy { skills.findByIdentifier(SkillId.M_GEM_LORE)?.id }

  /** Resolves the skill id at boot, so the tick never reaches the database for it. */
  fun warmUp() {
    gemLoreId
  }

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val gemLore = gemLoreId?.let { world.get(userId, KnownSkills::class)?.levelOf(it) } ?: 0
    if (gemLore < 1) {
      world.get(userId, Account::class)?.let { messages.sendToPlayer(it.accountId, OperationErrorSMSG(OpError.GEM_LORE_NEEDED)) }
      return false
    }

    val gem = items.idOf(roll()) ?: return false
    // Added to whatever the entity is already due this tick, because a second intent would replace the first.
    val due = world.get(userId, CreateItemsIntent::class)?.grants.orEmpty()
    world.add(userId, CreateItemsIntent(due + CreateItemIntent(gem, 1)))

    return true
  }

  private fun roll(): String {
    var left = random.nextInt(SHARES.sumOf { it.second })
    return SHARES.first { (_, share) -> left -= share; left < 0 }.first
  }

  companion object {
    /** `rough_gem` in items.yml. */
    const val ITEM_ID = 60L

    /** Each gem's share of Rough Gemstones, in percent: the rarer the gem, the rarer the stone that hides it. */
    val SHARES = listOf(
      "rough_amethyst" to 50,
      "rough_emerald" to 25,
      "rough_ruby" to 15,
      "rough_pyrelith" to 8,
      "rough_diamond" to 2,
    )
  }
}
