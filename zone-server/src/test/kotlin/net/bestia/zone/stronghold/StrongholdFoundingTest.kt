package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.ecs.EmpyriteSeedSprout
import net.bestia.zone.stronghold.persistence.StrongholdRecord
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrongholdFoundingTest {

  private val world = testWorld()
  private val config = StrongholdConfig(plantCoins = 10_000, siteBuildMinutes = 30)
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val held = mutableListOf<Stronghold>()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val sprouts = mockk<EmpyriteSeedSprouts>(relaxed = true)

  private val strongholds = mockk<StrongholdRegistry>(relaxed = true) {
    every { heldBy(any()) } answers { held.filter { it.guildId == firstArg<Long>() } }
    every { on(any()) } answers { held.firstOrNull { it.rank == firstArg<Int>() } }
    every { put(any()) } answers { held.add(firstArg()) }
  }

  private val structures = mockk<PlayerStructureService> {
    every {
      beginConstruction(any(), StaticEntityKind.EMPYRITE_SITE, MASTER_ID, ACCOUNT_ID, any(), 0f, 1800f)
    } answers { world.createEntity { id -> add(id, PlayerStructureIdentity(STRUCTURE_ID, ACCOUNT_ID)) } }
  }

  private val founding = StrongholdFounding(
    seeds = mockk { every { awake() } returns listOf(seed) },
    sprouts = sprouts,
    discoveries = mockk(relaxed = true),
    strongholds = strongholds,
    structures = structures,
    inventoryService = mockk(relaxed = true),
    commodities = mockk { every { coinItemId() } returns COIN },
    reserve = mockk(relaxed = true),
    worldService = mockk<WorldService> { every { config.voxelSize } returns 1.0 },
    world = mockk(relaxed = true),
    asyncJobExecutor = mockk<AsyncJobExecutor>(relaxed = true),
    messages = messages,
    config = config,
  )

  private val sprout = world.createEntity { id ->
    add(id, Position.fromVec3(Vec3L(1_000, 1_000, 0)))
    add(id, EmpyriteSeedSprout(seed.rank))
  }

  @Test
  fun `a first click offers the planting with its price`() {
    val member = memberAt(Vec3L(1_002, 1_000, 0))

    assertNull(founding.offer(world, member, sprout))

    verify { messages.sendToPlayer(ACCOUNT_ID, EmpyritePlantOfferSMSG(sprout, "Hochvein", 10_000, 30)) }
  }

  @Test
  fun `planting needs a rank that may use guild skills and a guild with Approval`() {
    val noRank = memberAt(Vec3L(1_000, 1_000, 0), mayUseGuildSkills = false)
    val noApproval = memberAt(Vec3L(1_000, 1_000, 0), approved = false)

    assertEquals(OpError.STRONGHOLD_NOT_ALLOWED, founding.offer(world, noRank, sprout))
    assertEquals(OpError.STRONGHOLD_NOT_ALLOWED, founding.plant(world, noApproval, sprout))
  }

  @Test
  fun `a guild founds only its first Stronghold`() {
    held.add(Stronghold(rank = 7, GUILD, StrongholdRecord.State.STANDING, null, EPOCH, EPOCH))

    assertEquals(OpError.STRONGHOLD_ALREADY_HELD, founding.plant(world, memberAt(Vec3L(1_000, 1_000, 0)), sprout))
  }

  @Test
  fun `the planter stands on the core and carries the coins`() {
    val beside = memberAt(Vec3L(1_006, 1_000, 0))
    val poor = memberAt(Vec3L(1_000, 1_000, 0), coins = 9_999)

    assertEquals(OpError.STRONGHOLD_NOT_ON_CORE, founding.plant(world, beside, sprout))
    assertEquals(OpError.STRONGHOLD_CANNOT_AFFORD, founding.plant(world, poor, sprout))
  }

  @Test
  fun `planting pays, raises a site of the guild where the sprout stood and records the hold`() {
    val member = memberAt(Vec3L(1_000, 1_000, 0), coins = 12_000)

    assertNull(founding.plant(world, member, sprout))

    assertEquals(2_000, world.get(member, Inventory::class)!!.getItems().sumOf { it.amount })
    val stronghold = assertNotNull(held.singleOrNull())
    assertEquals(Stronghold(2, GUILD, StrongholdRecord.State.SITE, STRUCTURE_ID, stronghold.siteWorkedAt, stronghold.plantedAt), stronghold)
    var siteGuild: Long? = null
    world.query(GuildOwned::class).each { siteGuild = get<GuildOwned>().guildId }
    assertEquals(GUILD, siteGuild)
    verify { sprouts.uproot(world, 2) }
  }

  @Test
  fun `a seed already planted cannot be planted again`() {
    held.add(Stronghold(rank = 2, OTHER_GUILD, StrongholdRecord.State.SITE, 99L, EPOCH, EPOCH))

    assertEquals(OpError.REQUEST_REFUSED, founding.plant(world, memberAt(Vec3L(1_000, 1_000, 0)), sprout))
    assertTrue(held.none { it.guildId == GUILD })
  }

  private fun memberAt(
    at: Vec3L,
    mayUseGuildSkills: Boolean = true,
    approved: Boolean = true,
    coins: Int = 10_000,
  ): EntityId {
    val skills = if (approved) mapOf(SkillId.OFFICIAL_GUILD_APPROVAL.name to 1) else emptyMap()

    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Master(MASTER_ID))
      add(id, Account(ACCOUNT_ID))
      add(id, GuildMembership(GUILD, taxPercent = 5, mayUseGuildSkills = mayUseGuildSkills, skillLevels = skills))
      add(id, Inventory(mutableListOf(Inventory.Item(COIN, coins))))
    }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    const val MASTER_ID = 5L
    const val ACCOUNT_ID = 7L
    const val COIN = 3L
    const val STRUCTURE_ID = 40L
    val EPOCH: java.time.Instant = java.time.Instant.EPOCH
  }
}
