package net.bestia.zone.questgen.plan

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import jakarta.annotation.PostConstruct
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.questgen.seed.QuestSeed
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * Every quest shape a designer can change, read from `quest-steps.yml` and `quest-motifs.yml`. A single quest
 * never has code of its own. A file the planner could not use is refused at boot.
 */
@Service
class QuestCatalogue {

  /** One thing a player does. [needs] and [gives] are plain statements such as `knows:site`. */
  data class StepPattern(
    val id: String,
    val kind: ObjectiveKind,
    val binds: BindSlot,
    val needs: Set<String>,
    val gives: Set<String>,
    val tension: Int,
    val text: String,
  )

  data class Motif(
    val id: String,
    val seed: QuestSeed.Kind,
    val goal: String,
    val steps: List<StepPattern>,
    val title: String,
    val decide: Decide?,
  )

  data class Decide(val text: String, val tension: Int, val choices: List<Choice>)

  /** [then] is the step that follows the choice; null ends the quest. */
  data class Choice(val id: String, val text: String, val then: StepPattern?)

  private var motifBySeed: Map<QuestSeed.Kind, Motif> = emptyMap()

  fun motifFor(seed: QuestSeed.Kind): Motif? {
    return motifBySeed[seed]
  }

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory()).addModule(kotlinModule()).build()
    val steps = mapper.readValue(ClassPathResource(STEPS).inputStream, StepsFile::class.java).steps
      .mapValues { (id, dto) ->
        require(dto.tension in 1..MAX_TENSION) { "$STEPS: '$id' has tension ${dto.tension}, wanted 1 to $MAX_TENSION" }
        StepPattern(id, dto.kind, dto.binds, dto.needs.toSet(), dto.gives.toSet(), dto.tension, dto.text)
      }

    fun stepOf(motif: String, id: String): StepPattern {
      return requireNotNull(steps[id]) { "$MOTIFS: '$motif' names step '$id', which $STEPS does not have" }
    }

    motifBySeed = mapper.readValue(ClassPathResource(MOTIFS).inputStream, MotifsFile::class.java).motifs
      .map { (id, dto) ->
        val decide = dto.decide?.let { decide ->
          require(decide.choices.size >= 2) { "$MOTIFS: '$id' offers fewer than two choices, so nothing is decided" }
          Decide(decide.text, decide.tension, decide.choices.map { (choice, c) -> Choice(choice, c.text, c.then?.let { stepOf(id, it) }) })
        }
        val motif = Motif(id, dto.seed, dto.goal, dto.steps.map { stepOf(id, it) }, dto.title, decide)
        require(motif.steps.any { motif.goal in it.gives }) { "$MOTIFS: no step of '$id' gives its goal '${motif.goal}'" }
        motif
      }
      .associateBy { it.seed }
  }

  private class StepsFile(val steps: Map<String, StepDto> = emptyMap())

  private class StepDto(
    val kind: ObjectiveKind,
    val binds: BindSlot,
    val needs: List<String> = emptyList(),
    val gives: List<String> = emptyList(),
    val tension: Int,
    val text: String,
  )

  private class MotifsFile(val motifs: Map<String, MotifDto> = emptyMap())

  private class MotifDto(
    val seed: QuestSeed.Kind,
    val goal: String,
    val steps: List<String>,
    val title: String,
    val decide: DecideDto? = null,
  )

  private class DecideDto(val text: String, val tension: Int, val choices: Map<String, ChoiceDto>)

  private class ChoiceDto(val text: String, val then: String? = null)

  private companion object {
    const val STEPS = "quest-steps.yml"
    const val MOTIFS = "quest-motifs.yml"
    const val MAX_TENSION = 3
  }
}
