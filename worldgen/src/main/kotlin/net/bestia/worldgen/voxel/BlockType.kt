package net.bestia.worldgen.voxel

/**
 * Whether a creature can move through one voxel of a material.
 *
 * A different question from [BlockType.solid], which asks whether the material can hold weight, and the two
 * genuinely differ: ice is solid and passable to nothing, a leaf canopy is neither solid nor an obstruction,
 * and lava is not solid but must stop everything. `solid` answers "can this be a floor" correctly for all
 * three already; this answers "can I stand in it", which it does not.
 *
 * It exists because `WalkableTile` used to answer the second question with a `when` over `AIR`, `WATER` and
 * `solid`, and **anything else fell through every arm and became free headroom**. That was right for leaves
 * by accident and would have been silently wrong for the first non-solid material that had to obstruct. A
 * declared property moves the default out of a derived structure, where no reader can see it, and into the
 * declaration table, where it is one column beside `solid` in the file everyone edits to add a material.
 */
enum class Passability {
  /**
   * Something can move through it.
   */
  OPEN,

  /**
   * A fluid a creature can be *in*.
   *
   * How much of it is too much is the agent's business, not the material's - see
   * [net.bestia.worldgen.derived.AgentProfile.maxWadeDepth].
   */
  SWIMMABLE,

  /** Nothing moves through it: every solid material, and lava. */
  BLOCKED
}

/**
 * The block palette.
 *
 * [id] is explicit and **permanent**: it goes into the RLE wire format, into chunk deltas, and into the
 * base hash the client compares against the server's. Ordinals would tie the format to the declaration
 * order of a Kotlin enum, which is exactly the kind of coupling that turns "insert a block type
 * alphabetically" into "every stored chunk in the world now decodes to the wrong rock".
 *
 * ### The gaps are gone
 *
 * The ids used to be sparse, in bands ten apart, and this file used to argue for keeping them that way: a
 * dense palette was measured as saving **zero bytes** against a wire format that packs material into six
 * bits, "because the only ids past 64 are ore and ore is rare". That measurement was correct and its
 * conclusion is now obsolete, because the thing it was conditional on changed. Deleting the building
 * materials - a building is an entity like a tree, not terrain, so it needs no material at all - took the
 * palette small enough that **every id fits in six bits**, ore included. `RleCodec`'s declined merged-run
 * format is worth re-measuring against this palette; the numbers in its KDoc were taken against the old one.
 *
 * The bands survive as *adjacency* rather than as round numbers: fluids, then rock, then unconsolidated,
 * then surface cover, then its blighted twins, then worked stone, then ore and gems. "Is this ore" is still
 * a range check a human can do while reading the file.
 *
 * What this costs, and it is a real cost: a new material now appends at the end of the palette rather than
 * landing beside its own kind, and **the headroom is two ids, not thirty**. The next material added pushes
 * the palette past 64 and closes the six-bit door again, at which point either it or the packing has to give.
 */
enum class BlockType(
  val id: Int,
  val solid: Boolean,

  /**
   * Whether a creature can move through one voxel of this.
   *
   * Defaults from [solid], so every existing material keeps exactly the behaviour it had, and only the
   * materials that disagree with their own solidity have to say so. Water is the one such today.
   */
  val passability: Passability = if (solid) Passability.BLOCKED else Passability.OPEN,

  /**
   * Whether a player can remove one voxel of this.
   *
   * Removal is the only terrain mutation the game has, so this is the whole of what a material can say about
   * being changed - there is no matching question about placing it.
   *
   * The fluids are the reason it exists. There is no runtime fluid state at all: `LavaWells`, `PondWater` and
   * `RiverWater` are generation-time samplers over immutable vector features, and at runtime water is a block
   * id with the same standing as granite. So a player who could carve water would leave a hole in a lake that
   * nothing would ever fill, and with no building system there is not even a way to wall it off afterwards.
   * Refusing is cheap, is legible to a player as "you cannot dig that", and needs no simulation.
   */
  val carvable: Boolean = true
) {

  AIR(0, solid = false, carvable = false),
  WATER(1, solid = false, passability = Passability.SWIMMABLE, carvable = false),
  ICE(2, solid = true),

  /**
   * Molten rock. In the fluid band beside water and ice because that is what it is, not in a free slot
   * further out: band membership is this file's documentation of what kind of thing a material is.
   *
   * `solid = false` does four separate jobs here and each is wanted. [VoxelChunk.highestSolid] reports the rock
   * *under* a pool, so nothing spawns on a lava surface; `WalkableTile` never treats it as a floor;
   * `ColumnSummary`'s `structural` is false, so a pool is not a roof and shelters nothing; and `highestNonAir`
   * still counts it, so the probe and the surface columns draw it with no change. `solid = true` would be
   * actively wrong - `highestSolid` would report the lava surface as ground to stand on.
   *
   * [Passability.BLOCKED] rather than inheriting `OPEN` from that: nothing walks into lava, and unlike water
   * there is no depth at which it becomes survivable, so there is no wading limit for it either.
   */
  LAVA(3, solid = false, passability = Passability.BLOCKED, carvable = false),

  // Basement.
  GRANITE(4, solid = true),

  /**
   * Oceanic basement, and what a volcano builds itself out of.
   *
   * Three readers, and each wants it for its own reason: `Stratigraphy` puts it under deep ocean rather than
   * granite, `SurfaceCover` caps a volcanic field in it because a crater wall is made of the cone and not of
   * the regional bedrock under it, and `ChunkMaterializer` floors a lava pool with it - the chilled crust on a
   * flow. Nothing else in the palette can say "dark igneous rock" for those three.
   */
  BASALT(5, solid = true),

  /**
   * Volcanic glass, quarried from the margin of a cooled flow.
   *
   * In the basement band because it is igneous rock, even though it is placed as a resource rather than as a
   * stratigraphic bed - `OreBlocks.PLAIN` maps it, the way marble maps to limestone. Not graded: obsidian is
   * not disseminated through host rock, it is a massive glassy body you cut blocks out of.
   */
  OBSIDIAN(6, solid = true),

  /**
   * Sedimentary rock, as one material.
   *
   * It was four - sandstone, shale, conglomerate and limestone - drawn per bed by a weighted hash, and the
   * four were worth one material each only while a player could tell them apart and do something different
   * with them. Nothing did: no yield, no hardness, no tool tier reads the bed, so the whole distinction lived
   * in four rows of a colour table. One rock and a banded cliff is the same picture for a quarter of the
   * palette.
   *
   * [LIMESTONE] is the exception that stayed, and the reason is below.
   */
  STONE(7, solid = true),

  /**
   * The one sedimentary rock kept apart from [STONE], because **caves dissolve it**.
   *
   * `Stratigraphy.SOLUBLE` is the whole justification: karst is where the limestone is, `CaveStage` gates
   * every passage on it, and an `Invariants` check asserts no passage ever stands anywhere else. Folding it
   * into `STONE` would have meant either caves everywhere sedimentary cover reaches, or a solubility flag
   * living on the bed rather than on the material - and the material is where a reader looks for it.
   *
   * It doubles as travertine at a geothermal basin, which is the same rock by a different route, so a hot
   * spring terrace and the karst country a cave system runs under come out the same white.
   */
  LIMESTONE(8, solid = true),

  // Unconsolidated.
  GRAVEL(9, solid = true),
  SAND(10, solid = true),
  DIRT(11, solid = true),

  /**
   * Saturated ground: peat, silt, and the floor of deep water.
   *
   * One material where there were two. `PEAT` and `CLAY` used to split the wetlands between them - an open
   * bog capped in fibrous peat against a swamp capped in silt under a closed canopy - and that distinction is
   * **deliberately given up here**. What is left to tell a bog from a swamp is the canopy over it, which is
   * the thing a player actually sees; two brown materials a metre apart were not carrying the difference.
   *
   * It is the one material with its own `SurfaceSlot` on the client rather than a tint over the soil texture,
   * and that is the whole reason it survives as a material at all: wet ground does not look like dry ground,
   * and no amount of tinting dirt makes it look wet. Blights to [BLIGHTED_DIRT] rather than getting a twin -
   * see the note there on why the corrupted set is three rows and not the whole palette.
   */
  MUD(12, solid = true),

  // Surface cover.
  GRASS(13, solid = true),

  /**
   * Bleached bunchgrass over bare earth: what [net.bestia.worldgen.bio.Biome.DRYLAND] is capped in.
   *
   * The one material added for a *visual* reason rather than a physical one, and worth justifying against
   * the instruction to reduce the palette. Dryland and grassland are 21% and 13% of the land between them and
   * both used to cap in `GRASS`, so a third of every world came out one colour - and the difference between
   * them is the whole reason grassland was kept out of the dry-grass merge. A block is the cheapest way to
   * say it: `SurfaceCover.cap` is a table with no noise source in it, so the alternative was threading a
   * dither field through the materialiser to mottle `DIRT` with `GRASS`, which is more machinery for a
   * worse-looking answer.
   *
   * Blights to `BLIGHTED_GRASS` rather than getting a twin of its own - see the note below on why the
   * corrupted set is three rows and not the whole palette.
   */
  DRY_GRASS(14, solid = true),
  SNOW(15, solid = true),

  /*
   * What the surface cover above becomes on corrupted ground.
   *
   * A twin per cover material rather than a flag on the block, because the wire format is a byte of block id
   * and nothing else - so a corrupted world costs the client three palette rows and no protocol change at all.
   * `voxel/SurfaceCover.blight` is the only thing that maps one to the other.
   *
   * Three, not the whole palette. Snow, ice, gravel, mud and the rocks are deliberately not twinned: snow on
   * cursed ground is still snow, a corrupted bog is dark either way, the bare biomes that use gravel are rare,
   * and blighting bedrock would turn every mine shaft inside a province purple for no gameplay reason.
   *
   * It was six. A blighted trunk and a blighted canopy were two of them, and a corrupted tree now carries
   * `PropFlags.BLIGHTED` instead - which is where a *flag* was the right answer all along, because a prop
   * already has attributes and a voxel is only ever a byte. `BLIGHTED_PEAT` was the third, and it went with
   * the peat.
   */
  BLIGHTED_GRASS(16, solid = true),
  BLIGHTED_DIRT(17, solid = true),
  BLIGHTED_SAND(18, solid = true),

  /**
   * Bridge decking, wall circuits, and the standing residue of history.
   *
   * The last worked material, and the survivor of a band that held six. Timber, plaster, thatch, roof tile,
   * plank and rubble were all here to build *houses* out of, and a house is an entity now - something a player
   * can enter, own and burn down - so none of them had a voxel left to fill. What stays is the worked stone
   * that genuinely is terrain: a bridge deck spans water no heightfield can express, a town wall is a
   * kilometre of standing geometry, and a ruin is masonry lying where a town used to be.
   */
  MASONRY(19, solid = true),

  /** A paved street surface. */
  COBBLESTONE(20, solid = true),

  /*
   * Ore, placed per voxel at chunk generation by sampling the sparse deposits. Never stored as a field.
   *
   * Three grades per ore, and the grade is the point: the block a player breaks has to say how much metal
   * falls out of it, and a single ORE_IRON cannot. `OreBlocks` owns the mapping in both directions -
   * (resource, grade) -> block for generation, block -> (resource, grade) for whatever eventually turns a
   * broken voxel into an item.
   *
   * Contiguous, three at a time, `SMALL` then `MEDIUM` then `RICH`. Both `viewer/Palette.graded` and the
   * client's own table index off `small.id + step`, so the spacing is load-bearing and not merely tidy.
   */
  ORE_COPPER_SMALL(21, solid = true),
  ORE_COPPER_MEDIUM(22, solid = true),
  ORE_COPPER_RICH(23, solid = true),

  ORE_TIN_SMALL(24, solid = true),
  ORE_TIN_MEDIUM(25, solid = true),
  ORE_TIN_RICH(26, solid = true),

  ORE_IRON_SMALL(27, solid = true),
  ORE_IRON_MEDIUM(28, solid = true),
  ORE_IRON_RICH(29, solid = true),

  ORE_GOLD_SMALL(30, solid = true),
  ORE_GOLD_MEDIUM(31, solid = true),
  ORE_GOLD_RICH(32, solid = true),

  ORE_SILVER_SMALL(33, solid = true),
  ORE_SILVER_MEDIUM(34, solid = true),
  ORE_SILVER_RICH(35, solid = true),

  ORE_MITHRANDIUM_SMALL(36, solid = true),
  ORE_MITHRANDIUM_MEDIUM(37, solid = true),
  ORE_MITHRANDIUM_RICH(38, solid = true),

  ROCK_SALT_SMALL(39, solid = true),
  ROCK_SALT_MEDIUM(40, solid = true),
  ROCK_SALT_RICH(41, solid = true),

  /** Fumarolic sulfur, bedded shallow at an active vent. Salt's geology, not a metal's. */
  ORE_SULFUR_SMALL(42, solid = true),
  ORE_SULFUR_MEDIUM(43, solid = true),
  ORE_SULFUR_RICH(44, solid = true),

  /**
   * Ore that came out of corrupted ground.
   *
   * Not a separate deposit and not a separate `MinableOre`: the body is iron or copper like any other, and
   * the mana in the rock around it is what makes what you dig out aetherite. The choice is made once per
   * deposit in `ChunkStructures.OreVeins`, so a body is entirely one or entirely the other and no player
   * ever finds a seam that changes metal halfway along.
   */
  ORE_AETHERITE_SMALL(45, solid = true),
  ORE_AETHERITE_MEDIUM(46, solid = true),
  ORE_AETHERITE_RICH(47, solid = true),

  /*
   * The gems.
   *
   * `GEM_` rather than `ORE_`, which breaks the band's naming, and the break is deliberate. The band is
   * "graded deposits the ore-vein machinery places", and a gem is one of those - `OreBlocks.GRADED` maps each
   * exactly like a metal, three grades in both directions - but calling one an ore would say it smelts, and it
   * does not. The prefix is the one word that carries the difference.
   *
   * Ordered by how easily a player finds one, cheapest first, which is also the order they were added in.
   */

  /**
   * Pyrelith: a gem grown in the gas cavities of a thick lava flow as it cooled.
   *
   * The world's first gem, and the reason to invent one rather than place a real mineral is `MITHRANDIUM`'s: a
   * material with no real-world counterpart is a material a player has to come here to learn about. Volcanic
   * only, by construction - see `ResourceStage.suitabilityFor`.
   */
  GEM_PYRELITH_SMALL(48, solid = true),
  GEM_PYRELITH_MEDIUM(49, solid = true),
  GEM_PYRELITH_RICH(50, solid = true),

  /**
   * Quartz geodes in soft, vuggy cover: the gem a player trips over.
   *
   * Deliberately the cheap and shallow one. Every other gem here is rare, deep and worth an expedition, which
   * left nothing at all for somebody digging their first shaft - so amethyst is two to forty-five metres down,
   * an order of magnitude more abundant than the rest, and worth about what salt is. A gem economy needs a
   * floor as much as it needs a ceiling.
   */
  GEM_AMETHYST_SMALL(51, solid = true),
  GEM_AMETHYST_MEDIUM(52, solid = true),
  GEM_AMETHYST_RICH(53, solid = true),

  /** Beryl in a granite pegmatite: tin's plutons, several times deeper and a great deal rarer. */
  GEM_EMERALD_SMALL(54, solid = true),
  GEM_EMERALD_MEDIUM(55, solid = true),
  GEM_EMERALD_RICH(56, solid = true),

  /**
   * Corundum in marble, at a collision belt.
   *
   * Shares `MARBLE`'s suitability terms on purpose rather than by accident - ruby genuinely is a
   * marble-hosted gem, so the ground that makes one makes the other. Depth is what separates them, exactly as
   * depth separates pyrelith from sulfur: marble is quarried off the surface and ruby is dug for.
   */
  GEM_RUBY_SMALL(57, solid = true),
  GEM_RUBY_MEDIUM(58, solid = true),
  GEM_RUBY_RICH(59, solid = true),

  /**
   * Kimberlite through the keel of a stable craton: the deepest thing in the world worth digging for.
   *
   * The only entry in the resource table that keys on being **away** from a plate boundary. A kimberlite pipe
   * erupts through crust that has been quiet for an age, never at an arc, and that anti-correlation is what
   * keeps diamond off the same ground as every other precious deposit - all of which want the arc. Old, worn
   * flat, and three hundred to eight hundred metres down.
   */
  GEM_DIAMOND_SMALL(60, solid = true),
  GEM_DIAMOND_MEDIUM(61, solid = true),
  GEM_DIAMOND_RICH(62, solid = true);

  companion object {
    private val BY_ID = arrayOfNulls<BlockType>(entries.maxOf { it.id } + 1).also { table ->
      for (block in entries) {
        require(table[block.id] == null) { "Duplicate block id ${block.id}" }
        table[block.id] = block
      }
    }

    /** @throws IllegalArgumentException on an unknown id, which means a version mismatch, not a bug. */
    fun of(id: Int): BlockType = BY_ID.getOrNull(id)
      ?: throw IllegalArgumentException("Unknown block id $id; the chunk was written by another version")

    fun ofOrNull(id: Int): BlockType? = BY_ID.getOrNull(id)
  }
}
