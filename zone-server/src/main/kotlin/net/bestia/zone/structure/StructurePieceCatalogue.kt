package net.bestia.zone.structure

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.entity.StaticEntityKind
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/** The cells each building piece fills, from `structure-pieces.yml`. */
@Service
class StructurePieceCatalogue {

  private class CellDto(
    val x: Int = 0,
    val y: Int = 0,
    val z: Int = 0,
    val fill: Double = 0.0,
    val walls: List<WallSide> = emptyList()
  )

  private class PieceDto(
    val kind: StaticEntityKind,
    val material: String,
    val cells: List<CellDto> = emptyList()
  )

  private class MaterialDto(
    val name: String,
    val verticalLoss: Double,
    val horizontalLoss: Double,
    val minStability: Double
  )

  private class CatalogueDto(
    val materials: List<MaterialDto> = emptyList(),
    val pieces: List<PieceDto> = emptyList()
  )

  private val byKind = HashMap<StaticEntityKind, StructurePiece>()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()

    val dto = ClassPathResource(RESOURCE).inputStream.use {
      mapper.readValue(it, CatalogueDto::class.java)
    }

    val materials = dto.materials.associate { it.name to toMaterial(it) }
    require(materials.size == dto.materials.size) { "$RESOURCE declares a material twice" }

    dto.pieces.forEach { piece ->
      require(piece.kind in PIECE_KINDS) { "$RESOURCE declares ${piece.kind}, which is not a building piece" }
      val material = requireNotNull(materials[piece.material]) { "${piece.kind} is made of unknown ${piece.material}" }
      require(byKind.put(piece.kind, toPiece(piece, material)) == null) { "$RESOURCE declares ${piece.kind} twice" }
    }

    val missing = PIECE_KINDS.filterNot { it in byKind }
    require(missing.isEmpty()) { "$RESOURCE describes no ${missing.joinToString()}" }

    LOG.info { "Loaded ${byKind.size} building pieces" }
  }

  fun isPiece(kind: StaticEntityKind): Boolean {
    return kind in byKind
  }

  fun of(kind: StaticEntityKind): StructurePiece {
    return byKind[kind] ?: throw IllegalStateException("no building piece registered for $kind")
  }

  private fun toMaterial(dto: MaterialDto): StructureMaterial {
    require(dto.verticalLoss in 0.0..1.0 && dto.horizontalLoss in 0.0..1.0) { "${dto.name} loses more than it has" }
    require(dto.minStability in 0.0..1.0) { "${dto.name} has min-stability ${dto.minStability}" }

    return StructureMaterial(dto.name, dto.verticalLoss, dto.horizontalLoss, dto.minStability)
  }

  private fun toPiece(dto: PieceDto, material: StructureMaterial): StructurePiece {
    require(dto.cells.isNotEmpty()) { "${dto.kind} fills no cell" }

    val cells = dto.cells.map { cell ->
      require(cell.fill in 0.0..1.0) { "${dto.kind} has a cell with fill ${cell.fill}" }
      require(cell.fill > 0.0 || cell.walls.isNotEmpty()) { "${dto.kind} has a cell with neither fill nor walls" }

      val edges = cell.walls.fold(0) { edges, side -> edges or side.edge }
      StructurePiece.Cell(cell.x, cell.y, cell.z, Occupancy.of(cell.fill), edges)
    }
    val positions = cells.map { Triple(it.x, it.y, it.z) }
    require(positions.toSet().size == positions.size) { "${dto.kind} lists a cell twice" }

    return StructurePiece(dto.kind, cells, material)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val RESOURCE = "structure-pieces.yml"

    val PIECE_KINDS = setOf(
      StaticEntityKind.STRUCTURE_WALL,
      StaticEntityKind.STRUCTURE_FLOOR,
      StaticEntityKind.STRUCTURE_STAIRS
    )
  }
}
