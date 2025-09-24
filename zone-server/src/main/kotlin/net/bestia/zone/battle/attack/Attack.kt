package net.bestia.zone.battle.attack

import jakarta.persistence.*
import net.bestia.zone.util.requireValidIdentifier

@Entity
@Table(
  name = "attack",
  indexes = [
    Index(columnList = "identifier", unique = true)
  ]
)
class Attack(
  @Column(nullable = false)
  val identifier: String,

  @Column(nullable = true)
  val strength: Int?,

  @Column(nullable = false)
  val type: AttackType,

  @Column(nullable = true)
  val script: String?,

  @Column(nullable = false)
  val manaCost: Int,

  @Column(nullable = true)
  val range: Int?,

  val needsLineOfSight: Boolean
) {

  init {
    requireValidIdentifier(identifier)

    // No damage attacks are required to have a script and strength set to null.
    if (type == AttackType.NO_DAMAGE) {
      requireNotNull(script) {
        "Attack $identifier is NO_DAMAGE and must have a script attached"
      }
      require(strength == null) {
        "Attack $identifier is NO_DAMAGE and must have strength set to null"
      }
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}