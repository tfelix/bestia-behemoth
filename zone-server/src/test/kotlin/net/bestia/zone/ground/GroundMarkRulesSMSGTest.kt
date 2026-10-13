package net.bestia.zone.ground

import net.bestia.worldgen.voxel.BlockType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules a client lays its own marks by.
 *
 * Every number comes from a live config or from the tables that decide which ground takes a mark, so each test
 * retunes one and expects the message to follow - a constant copied into this class would pass the defaults and
 * drift on the first restart that changes them.
 */
class GroundMarkRulesSMSGTest {

  private fun rules(
    stamps: GroundStampConfig = GroundStampConfig(),
    wear: GroundWearConfig = GroundWearConfig(),
    blood: GroundBloodConfig = GroundBloodConfig(),
    speedFactor: Double = 3.0,
  ): GroundMarkRulesSMSG {
    return GroundMarkRulesSMSG.of(stamps, wear, blood, speedFactor)
  }

  /** The client ages a print in real time, and the config is in Bestia seconds. */
  @Test
  fun `a print lasts its Bestia life divided by the speed factor, in real milliseconds`() {
    assertEquals(1_200_000, rules(GroundStampConfig(footprintTtlSeconds = 3_600), speedFactor = 3.0).stampLifetimeMillis)
    assertEquals(150_000, rules(GroundStampConfig(footprintTtlSeconds = 600), speedFactor = 4.0).stampLifetimeMillis)
    assertEquals(1_000, rules(GroundStampConfig(footprintTtlSeconds = 1), speedFactor = 1.0).stampLifetimeMillis)
  }

  @Test
  fun `a life too long for the wire is clamped rather than sent negative`() {
    val huge = GroundStampConfig(footprintTtlSeconds = Long.MAX_VALUE)

    assertEquals(Int.MAX_VALUE, rules(huge).stampLifetimeMillis)
  }

  @Test
  fun `the ring holds what the stamp config says`() {
    assertEquals(12, rules(GroundStampConfig(maxStampsPerColumn = 12)).stampRingCapacity)
  }

  @Test
  fun `a print is taken by exactly the blocks that hold one`() {
    val expected = SurfaceTrampleableGround.CAP_IMPRESSION.keys.map { it.id }.sorted()

    assertEquals(expected, rules().stampBlockIds)
    assertTrue(BlockType.SNOW.id in expected)
    assertTrue(BlockType.COBBLESTONE.id !in expected, "paving takes no print")
  }

  @Test
  fun `wear settings are the wear config's`() {
    val wear = rules(
      wear = GroundWearConfig(fadeSeconds = 1_000, stepWeight = 9, neighbourWeight = 3, visibleThreshold = 40)
    ).wear

    assertEquals(1_000, wear.fadeSeconds)
    assertEquals(9, wear.stepWeight)
    assertEquals(3, wear.neighbourWeight)
    assertEquals(40, wear.visibleThreshold)
  }

  /** A weight of 0.8 sent as an integer would be 0, and dry grass would stop wearing on every client. */
  @Test
  fun `block weights are the wear table's, as doubles`() {
    val weights = rules().wear.blockWeights

    assertEquals(SurfaceTrampleableGround.CAP_WEAR.mapKeys { it.key.id }, weights)
    assertEquals(1.0, weights[BlockType.GRASS.id])
    assertEquals(0.8, weights[BlockType.DRY_GRASS.id])
    assertEquals(0.4, weights[BlockType.DIRT.id])
    assertNull(weights[BlockType.COBBLESTONE.id], "paving does not wear")
  }

  @Test
  fun `blood settings are the blood config's`() {
    val blood = rules(
      blood = GroundBloodConfig(fadeSeconds = 2_000, spillWeight = 100, spillRadiusTiles = 3, visibleThreshold = 20)
    ).blood

    assertEquals(2_000, blood.fadeSeconds)
    assertEquals(100, blood.spillWeight)
    assertEquals(3, blood.spillRadiusTiles)
    assertEquals(20, blood.visibleThreshold)
  }

  @Test
  fun `the wire form carries every field`() {
    val message = rules().toBnetEnvelope().groundMarkRules

    assertEquals(1_200_000, message.stampLifetimeMillis)
    assertEquals(48, message.stampRingCapacity)
    assertEquals(SurfaceTrampleableGround.CAP_IMPRESSION.size, message.stampBlocksCount)

    assertEquals(259_200, message.wear.fadeSeconds)
    assertEquals(32, message.wear.visibleThreshold)
    assertEquals(6, message.wear.stepWeight)
    assertEquals(2, message.wear.neighbourWeight)

    assertEquals(432_000, message.blood.fadeSeconds)
    assertEquals(16, message.blood.visibleThreshold)
    assertEquals(220, message.blood.spillWeight)
    assertEquals(2, message.blood.spillRadiusTiles)
  }

  @Test
  fun `block weights go on the wire in id order, with the fraction intact`() {
    val weights = rules().toBnetEnvelope().groundMarkRules.wear.blockWeightsList

    assertEquals(weights.map { it.blockId }.sorted(), weights.map { it.blockId })
    assertEquals(0.8, weights.single { it.blockId == BlockType.DRY_GRASS.id }.weight)
  }

  @Test
  fun `stamp blocks go on the wire in id order`() {
    val blocks = rules().toBnetEnvelope().groundMarkRules.stampBlocksList

    assertEquals(blocks.sorted(), blocks)
  }
}
