package net.bestia.zone.questgen.persistence

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.worldgen.core.ActorType
import net.bestia.zone.questgen.plan.QuestPlan
import net.bestia.zone.questgen.seed.QuestSeed

/** A plan and how far the world has come with it. The plan itself is never changed. */
@Entity
@Table(name = "quest_plan", indexes = [Index(columnList = "state")])
class StoredQuestPlan(
  @Convert(converter = QuestPlanConverter::class)
  @Column(columnDefinition = "TEXT", nullable = false)
  val plan: QuestPlan,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /** The seed again, in columns of their own: "is there a plan for this artifact" is asked for every seed. */
  @Enumerated(EnumType.STRING)
  @Column(name = "seed_kind", nullable = false, length = 32)
  val seedKind: QuestSeed.Kind = plan.seed.kind

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 16)
  val subjectType: ActorType = plan.seed.subject.type

  @Column(name = "subject_index", nullable = false)
  val subjectIndex: Int = plan.seed.subject.index

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var state: State = State.PLANNED

  /** The quest of the quest core the plan runs as. Set when the first master accepts. */
  @Column(name = "quest_id")
  var questId: Long? = null

  enum class State {
    /** Worked out, and not offered yet. */
    PLANNED,

    /** Where the plan starts, the quest can be picked up. */
    OFFERED,

    /** A master accepted it. Others may still join. */
    RUNNING,
    ENDED;

    val canBeAccepted: Boolean
      get() = this == OFFERED || this == RUNNING
  }
}
