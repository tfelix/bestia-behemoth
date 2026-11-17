package net.bestia.zone.quest.persistence

import com.fasterxml.jackson.core.type.TypeReference
import jakarta.persistence.Converter
import net.bestia.zone.quest.QuestObjective

@Converter
class QuestObjectivesConverter : JsonListConverter<QuestObjective>(object : TypeReference<List<QuestObjective>>() {})
