package net.bestia.model.party

data class PartyMemberDTO(
    val playerBestiaId: Long,
    val entityId: Long,
    val name: String,
    val masterName: String
)