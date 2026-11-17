package net.bestia.zone.quest.persistence

import com.fasterxml.jackson.core.type.TypeReference
import jakarta.persistence.Converter
import net.bestia.zone.quest.QuestTextArg

@Converter
class QuestTextArgsConverter : JsonListConverter<QuestTextArg>(object : TypeReference<List<QuestTextArg>>() {})
