package net.bestia.zone.breeding.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.Sex
import net.bestia.zone.breeding.Clutch

/**
 * What one egg item will hatch into, keyed by that item's instance. The instance outlives every move - bag, farm,
 * incubator, ground - so the egg is always found by it.
 */
@Entity
@Table(name = "bestia_egg")
class BestiaEgg(
  @Id
  @Column(name = "item_instance_id")
  val itemInstanceId: Long,

  @Column(name = "species_id", nullable = false)
  val speciesId: Long,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val sex: Sex,

  @Embedded
  val individualValues: IndividualValues,

  /** `skillId:learnLevel` pairs, comma separated. At most three, so a column beats a table. */
  @Column(name = "attack_learn_levels", nullable = false, length = 100)
  private val encodedAttackLearnLevels: String,

  @Column(name = "hatch_millis", nullable = false)
  val hatchMillis: Long,

  /** How much of [hatchMillis] it has spent in incubators, kept when it is taken out. */
  @Column(name = "hatched_millis", nullable = false)
  var hatchedMillis: Long = 0,
) {

  val attackLearnLevels: Map<Long, Int>
    get() {
      if (encodedAttackLearnLevels.isEmpty()) return emptyMap()

      return encodedAttackLearnLevels.split(',').associate { pair ->
        val (skillId, learnLevel) = pair.split(':')
        skillId.toLong() to learnLevel.toInt()
      }
    }

  companion object {
    fun of(itemInstanceId: Long, clutch: Clutch): BestiaEgg {
      return BestiaEgg(
        itemInstanceId = itemInstanceId,
        speciesId = clutch.speciesId,
        sex = clutch.sex,
        individualValues = clutch.ivs,
        encodedAttackLearnLevels = clutch.attackLearnLevels.entries.joinToString(",") { "${it.key}:${it.value}" },
        hatchMillis = clutch.hatchTime.toMillis(),
      )
    }
  }
}
