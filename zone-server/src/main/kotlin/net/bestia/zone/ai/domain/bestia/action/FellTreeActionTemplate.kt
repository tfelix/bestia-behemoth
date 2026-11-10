package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.BasicAttack
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.bt.selector
import net.bestia.zone.ai.bt.sequence
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.AttackDefinition
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.DefaultAttackDefinition
import net.bestia.zone.ai.domain.bestia.Gatherable
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.CombatTargetPromotion
import net.bestia.zone.battle.attack.AttackExecutionService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.LootProtection
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId

/**
 * Fells a tree a gatherer saw with its own attack, as a player does, and picks up the logs and resin it drops,
 * grounding [BestiaDomain.Goals.GATHER]. Not grounded for a species without a default attack.
 */
class FellTreeActionTemplate(
  private val locomotion: Locomotion,
  private val attackExecution: AttackExecutionService,
  private val promotion: CombatTargetPromotion,
  private val aoi: EntityAOIService,
  attacks: List<AttackDefinition>,
  private val maxCandidates: Int = 3,
) : ActionTemplate {
  override val id = "fellTree"

  private val axe = attacks.filterIsInstance<DefaultAttackDefinition>().minByOrNull { it.range }

  override fun ground(state: WorldState): List<Action> {
    val axe = axe ?: return emptyList()
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val seen = state.get(BestiaDomain.GATHERABLES) ?: return emptyList()

    return seen
      .filter { it.work == Gatherable.Work.FELL }
      .sortedBy { it.position.distance(position) }
      .take(maxCandidates)
      .map { tree ->
        Action(
          name = "fellTree(${tree.propId})",
          effects = GatherWork.effectsOf(tree),
          // A tree takes many swings where a stick takes one bend, so loose things nearby go first.
          cost = { tree.position.distance(position).toFloat() + FELLING_COST },
          behavior = {
            GatherWork.attempt(tree, sequence {
              +MoveTo(tree.position, locomotion, arrivalRadius = axe.range)
              +Promote(tree, promotion, axe.range)
              +fellUntilDown(tree.entityId, axe)
              +MoveTo(tree.position, locomotion, arrivalRadius = LOOT_REACH)
              +PickUpDrops(tree.position, aoi)
            })
          },
        )
      }
  }

  private fun fellUntilDown(treeId: EntityId, axe: DefaultAttackDefinition) = selector {
    // Dead a tick before it is gone, and a swing at it then would fail the whole felling.
    condition("tree is down") { ctx -> ctx.world.has(treeId, Dead::class) || !ctx.world.isAlive(treeId) }
    sequence {
      +BasicAttack(treeId, attackExecution, axe.attack)
      run("still standing") { Status.RUNNING }
    }
  }

  /**
   * Makes the tree a combat entity before the first swing. Inside the act stage the promotion's components land
   * at the end of the tick, and a swing before then would fizzle, so this waits for them. Done for good once
   * promoted: the sequence ticks it again after the tree has gone.
   */
  private class Promote(
    private val tree: Gatherable,
    private val promotion: CombatTargetPromotion,
    private val reach: Long,
  ) : BtNode {
    private var promoted = false

    override fun tick(context: BtContext): Status {
      if (promoted) return Status.SUCCESS
      if (context.world.has(tree.entityId, Position::class)) {
        promoted = true
        return Status.SUCCESS
      }
      if (!GatherWork.stillStands(context, tree)) return Status.FAILURE

      val from = context.world.get(context.entityId, Position::class)?.toVec3L() ?: return Status.FAILURE
      val promoting = promotion.promoteIfNeeded(context.world, tree.entityId, from, reach + 1)
      return if (promoting) Status.RUNNING else Status.FAILURE
    }

    override fun toString(): String = "Promote(${tree.propId})"
  }

  /**
   * Loots the drops at [at] that are its master's kill loot, one pickup in flight at a time. Done once none is
   * left, after giving the drops the moment they need to appear.
   */
  private class PickUpDrops(private val at: Vec3L, private val aoi: EntityAOIService) : BtNode {
    private val tried = HashSet<EntityId>()
    private var firstEmptyLookTick: Long? = null

    override fun tick(context: BtContext): Status {
      val world = context.world
      // One component per class: a second intent added now would be lost to the first one's removal.
      if (world.has(context.entityId, ObtainItemIntent.LootItemIntent::class)) return Status.RUNNING

      val account = world.get(context.entityId, Account::class)?.accountId ?: return Status.SUCCESS
      val drop = aoi.queryEntitiesInCube(at, LOOT_REACH * 2, AoiLayer.DYNAMIC_ONLY).firstOrNull { id ->
        id !in tried && world.has(id, GroundItemStack::class) &&
          world.get(id, LootProtection::class)?.ownerAccountId == account
      }

      if (drop == null) {
        val since = firstEmptyLookTick ?: context.currentTick.also { firstEmptyLookTick = it }
        val settled = context.currentTick - since >= context.ticksFor(DROP_SETTLE_SECONDS)
        return if (settled) Status.SUCCESS else Status.RUNNING
      }

      tried.add(drop)
      world.add(context.entityId, ObtainItemIntent.LootItemIntent(drop))
      return Status.RUNNING
    }

    override fun toString(): String = "PickUpDrops($at)"
  }

  companion object {
    private const val FELLING_COST = 10f

    /** `ObtainItemIntentSystem`'s reach for a stack on the ground. */
    private const val LOOT_REACH = 1L

    private const val DROP_SETTLE_SECONDS = 0.5f

    val READS: ComponentClassSet = setOf(Account::class, GroundItemStack::class, LootProtection::class)
    val WRITES: ComponentClassSet = setOf(ObtainItemIntent.LootItemIntent::class)
  }
}
