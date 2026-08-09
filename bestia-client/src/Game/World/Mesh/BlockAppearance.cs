using System.Collections.Generic;
using Godot;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// The block palette, and the two things the mesher needs from it: which blocks belong to which surface,
  /// and what colour each one is.
  /// </summary>
  /// <remarks>
  /// <see cref="Palette"/> mirrors the server's <c>BlockType</c> enum by hand. It used to be sent over the
  /// wire, on the argument that a renamed material should not be a client release - but a *new* material is
  /// one regardless, because nothing here can invent a colour for it that looks like rock rather than like a
  /// bug, and a renamed one changes nothing the player sees. So the transfer bought a few hundred bytes per
  /// login and a whole message type in exchange for a property nobody could use. What replaces it is
  /// <c>ChunkEngine.Version</c>: the client states which palette it holds, and the server says whether that
  /// is the one it is encoding against.
  ///
  /// <para>
  /// The lookup tables are indexed directly by block id, because they are read in the mesher's innermost loop
  /// and ids are only ever a byte on the wire. Sparse ids waste a couple of hundred entries, which is the
  /// whole cost of not having a dictionary lookup per voxel.
  /// </para>
  ///
  /// <para>
  /// Surface membership comes from <see cref="Block.Surface"/> rather than from hardcoded ids: <c>ICE</c> is
  /// solid and belongs to the terrain surface, <c>WATER</c> is not and gets its own transparent one.
  /// </para>
  ///
  /// <para>
  /// It used to come from <see cref="Block.Solid"/>, and a leaf canopy is why it no longer does. Solidity is
  /// the server's word for "does this obstruct" - it decides pathing, spawn heights and line of sight - and a
  /// canopy was deliberately not solid so that agents walk under it. Which mesh a material is drawn into is a
  /// different question with a different answer: leaves were opaque green and belonged on the terrain surface,
  /// not blended into the transparent water one, where a tree beside a lake would have merged with it. Leaves
  /// have left the palette - they are entities now - but the distinction they established is what <c>LAVA</c>
  /// relies on below, so it outlived them.
  /// </para>
  ///
  /// <para>
  /// <c>LAVA</c> is the second material where those two answers diverge and the first that needed a mesh of its
  /// own for it. Leaves could share the terrain surface because they wanted the same material; lava wants
  /// emission, and <c>StandardMaterial3D</c> has no per-vertex emission - so a lava pool drawn on the terrain
  /// surface would be a matte orange patch indistinguishable from orange rock, which loses the entire visual
  /// point of the material. See <see cref="Molten"/>.
  /// </para>
  ///
  /// <para>
  /// <b>Colour is no longer albedo.</b> It used to be - vertex colour was the interim answer to texturing, and
  /// this file's whole output was one <see cref="Color"/> per material. It is now a <i>tint</i>, multiplied over
  /// the texture the material's <see cref="SurfaceSlot"/> supplies. The table did not change and neither did any
  /// of the reasoning behind the individual colours, because tinting rock toward gold and painting rock gold want
  /// the same number; what changed is that the number is now one of two things a material declares rather than
  /// all of it. See <see cref="SurfaceSlot"/> for the other, and for why sixty materials need only eight.
  /// </para>
  /// </remarks>
  public sealed class BlockAppearance
  {
    /// <summary>Block ids are a byte on the wire, so every table covers the whole range.</summary>
    public const int Ids = 256;

    /// <summary>Which of the chunk's meshes a material is drawn into.</summary>
    public enum SurfaceKind
    {
      /// <summary>The opaque terrain mesh.</summary>
      Terrain,

      /// <summary>The transparent water mesh.</summary>
      Water,

      /// <summary>The opaque, emissive lava mesh.</summary>
      Lava
    }

    /// <summary>How many surfaces a chunk is meshed into. Keep in step with <see cref="SurfaceKind"/>.</summary>
    public const int SurfaceKinds = 3;

    /// <summary>
    /// Which of the terrain shader's eight texture layers a material is drawn from.
    /// </summary>
    /// <remarks>
    /// <b>A slot is a physical archetype, not a material.</b> Sixty block types share eight of these because what
    /// a slot supplies is mesostructure - the grain of sand, the fracture of rock, the blades of grass - while
    /// <see cref="Block.Colour"/> supplies the hue on top of it. Gold ore is a gold-tinted rock texture, and that
    /// is not a compromise: it is what makes the grade legible at all, because a rich seam and its host rock read
    /// as the same stone in two colours, which is exactly what they are.
    ///
    /// <para>
    /// The split is what lets the whole palette render from day one. A material nobody has authored a texture for
    /// lands on <see cref="Neutral"/> and is drawn as its palette colour over flat grey - which is precisely what
    /// the vertex-colour renderer did, so nothing regresses while art lands slot by slot.
    /// </para>
    ///
    /// <para>
    /// <b>Ordinals are the shader's layer indices</b>, in <c>CUSTOM0</c> for 0-3 and <c>CUSTOM1</c> for 4-7. They
    /// are a wire format between this file and <c>terrain.gdshader</c>'s texture arrays and uniform arrays, so
    /// reordering them silently repaints the world. Append only.
    /// </para>
    ///
    /// <para>
    /// <see cref="Neutral"/> is zero deliberately. <see cref="_slot"/> is a flat 256-entry table and a block id
    /// the palette does not know reads whatever the array was initialised to; grey is a material nobody notices,
    /// grass in the middle of a mountain is a bug report.
    /// </para>
    /// </remarks>
    public enum SurfaceSlot
    {
      /// <summary>Flat grey. Every unmapped id, and every material whose texture has not been authored yet.</summary>
      Neutral = 0,

      Grass = 1,

      /// <summary>Bleached bunchgrass. Its own slot for the reason <c>DRY_GRASS</c> is its own block.</summary>
      DryGrass = 2,

      Sand = 3,

      /// <summary>Loose earth: dirt, clay and peat.</summary>
      Soil = 4,

      /// <summary>Every bed and every ore body. The tint is what tells granite from a gold seam.</summary>
      Rock = 5,

      /// <summary>Permanent snowpack and ice, as placed by the generator - not the weather overlay.</summary>
      Snow = 6,

      /// <summary>Unused. Held open so the first material that genuinely needs its own texture has somewhere to go.</summary>
      Reserved = 7
    }

    /// <summary>
    /// How many slots the shader blends between. Eight, and the eight are not arbitrary.
    /// </summary>
    /// <remarks>
    /// One <c>RGBA8</c> vertex attribute holds four weights, so eight is two attributes - <c>CUSTOM0</c> and
    /// <c>CUSTOM1</c> - and sixteen would be all four Godot offers with nothing left for anything else. Keep in
    /// step with <see cref="SurfaceSlot"/> and with the array uniforms in <c>terrain.gdshader</c>.
    /// </remarks>
    public const int Slots = 8;

    /// <summary>One material, exactly as the server's <c>BlockType</c> declares it, plus how to draw it.</summary>
    public sealed class Block
    {
      /// <summary>
      /// The id that appears in chunk payloads and in stored deltas.
      /// </summary>
      /// <remarks>
      /// Sparse and permanent - grouped by material family with gaps inside each family - so this is
      /// emphatically not an index into <see cref="Palette"/>.
      /// </remarks>
      public byte Id { get; init; }

      public string Name { get; init; } = "";

      /// <summary>
      /// Whether it obstructs: the server's <c>BlockType.solid</c>, mirrored.
      /// </summary>
      /// <remarks>
      /// Unread here now that <see cref="Surface"/> answers the meshing question, and mirrored anyway for the
      /// same reason <see cref="Opaque"/> is: the two palettes have to be comparable by eye, and a field that
      /// disappears from this side is a divergence nobody can see.
      /// </remarks>
      public bool Solid { get; init; }

      /// <summary>Which mesh it is drawn into.</summary>
      public SurfaceKind Surface { get; init; }

      /// <summary>
      /// Which texture layer it is drawn from, once the terrain shader is doing the drawing.
      /// </summary>
      /// <remarks>
      /// Stated per material rather than derived from the id's family, for the reason the fixtures give about
      /// <see cref="Surface"/>: a rule that happens to be true of every row today is a trap the moment a row
      /// contradicts it, and it fails by rendering rather than by throwing.
      /// </remarks>
      public SurfaceSlot Slot { get; init; }

      /// <summary>
      /// Whether it blocks sight.
      /// </summary>
      /// <remarks>
      /// Never distinct from <see cref="Solid"/> in the current palette and unread by anything here, but
      /// mirrored because the server distinguishes them and a divergence would be silent.
      /// </remarks>
      public bool Opaque { get; init; }

      public Color Colour { get; init; }
    }

    /// <summary>
    /// Every material the server can put in a chunk, in id order.
    /// </summary>
    /// <remarks>
    /// Must stay in step with <c>BlockType.kt</c>. <c>ChunkStoreTest.the block palette is pinned</c> is the
    /// tripwire: it fails the server build whenever the enum's ids or names change, at which point this table
    /// and <c>ChunkEngine.Version</c> on both sides have to move with it.
    /// </remarks>
    public static readonly IReadOnlyList<Block> Palette = new[]
    {
      // AIR (0) is absent on purpose: it is the wire format's definition of "nothing here" rather than a
      // material, and an id with no entry is drawn by neither surface, which is what air should be.
      Fluid(1, "WATER", 0.16f, 0.35f, 0.52f, 0.72f),
      Terrain(2, "ICE", SurfaceSlot.Snow, 0.78f, 0.88f, 0.93f),
      Molten(3, "LAVA", 0.95f, 0.36f, 0.09f),

      // Basement.
      Terrain(10, "GRANITE", SurfaceSlot.Rock, 0.60f, 0.56f, 0.55f),
      Terrain(11, "BASALT", SurfaceSlot.Rock, 0.26f, 0.26f, 0.28f),

      // Volcanic glass. Darker and cooler than basalt, which is the only thing it is ever next to.
      Terrain(12, "OBSIDIAN", SurfaceSlot.Rock, 0.09f, 0.08f, 0.11f),

      // Sedimentary cover.
      Terrain(20, "LIMESTONE", SurfaceSlot.Rock, 0.78f, 0.76f, 0.68f),
      Terrain(21, "SANDSTONE", SurfaceSlot.Rock, 0.76f, 0.62f, 0.42f),
      Terrain(22, "SHALE", SurfaceSlot.Rock, 0.34f, 0.35f, 0.36f),
      Terrain(23, "CONGLOMERATE", SurfaceSlot.Rock, 0.57f, 0.51f, 0.45f),

      // Unconsolidated. GRAVEL is rock rather than soil: it is what a scree slope and a river bed are made of,
      // and a loose-earth texture under it would read as mud in exactly the places water has washed the fines out.
      Terrain(30, "GRAVEL", SurfaceSlot.Rock, 0.52f, 0.50f, 0.48f),
      Terrain(31, "SAND", SurfaceSlot.Sand, 0.85f, 0.76f, 0.55f),
      Terrain(32, "CLAY", SurfaceSlot.Soil, 0.60f, 0.45f, 0.36f),
      Terrain(33, "DIRT", SurfaceSlot.Soil, 0.38f, 0.28f, 0.19f),
      Terrain(34, "PEAT", SurfaceSlot.Soil, 0.22f, 0.17f, 0.13f),

      // 35 was PERMAFROST, mechanically identical to DIRT server-side; folded back in on the palette pass
      // and its id left free rather than reused, mirroring worldgen's BlockType.

      // Surface cover.
      Terrain(40, "GRASS", SurfaceSlot.Grass, 0.28f, 0.45f, 0.19f),
      Terrain(41, "SNOW", SurfaceSlot.Snow, 0.92f, 0.94f, 0.96f),
      // Bleached bunchgrass: what DRYLAND caps in, and deliberately a long way from GRASS in hue. Grassland and
      // dryland are a third of the land between them and used to share id 40, so telling them apart is the whole
      // reason this row exists - a subtle difference here would put the two biomes back into one colour.
      Terrain(42, "DRY_GRASS", SurfaceSlot.DryGrass, 0.61f, 0.57f, 0.33f),

      // Ids 45 to 48 are free and must stay free: LOG, LEAVES, MANA_CRYSTAL_SMALL and MANA_CRYSTAL_LARGE.
      // Trees and mana crystals are entities now, delivered by ChunkStaticEntitiesSMSG and drawn by
      // StaticEntityRenderer, so nothing in a chunk payload names them. See BlockType's own note.

      // Corrupted ground. Each is its clean twin pulled toward violet and darkened, so a corruption boundary
      // reads as the same terrain gone wrong rather than as a different biome - which is what it is.
      //
      // Each therefore takes its clean twin's slot, which is that same argument one layer down: the corrupted
      // ground is the same ground, so it wants the same grain and only a different colour. Giving the four of
      // them a slot of their own would make a blight boundary a change of *texture*, and the boundary would read
      // as two different materials meeting rather than as one material going wrong.
      Terrain(49, "BLIGHTED_GRASS", SurfaceSlot.Grass, 0.30f, 0.24f, 0.34f),
      Terrain(50, "BLIGHTED_DIRT", SurfaceSlot.Soil, 0.26f, 0.18f, 0.24f),
      Terrain(51, "BLIGHTED_SAND", SurfaceSlot.Sand, 0.56f, 0.46f, 0.54f),
      Terrain(52, "BLIGHTED_PEAT", SurfaceSlot.Soil, 0.17f, 0.12f, 0.18f),
      // 53 and 54 free: BLIGHTED_LOG and BLIGHTED_LEAVES. A corrupted tree carries a flag on its prop now.

      // Bridge decking and other worked structure.
      //
      // The worked materials are all on Neutral, and that is a holding position rather than a judgement. Thatch
      // and plaster want their own textures - they are the only things in the palette with a man-made weave - but
      // one shared "worked" texture would be worse than none, because a plastered wall and a thatched roof drawn
      // with the same grain in two colours reads as a rendering fault, where flat colour reads as unfinished art.
      // Each gets a slot when it gets a texture; Reserved is where the first one goes.
      Terrain(60, "MASONRY", SurfaceSlot.Neutral, 0.62f, 0.60f, 0.56f),

      // Buildings and streets, added with the server's step 8. Deliberately more saturated than the natural
      // materials they stand on: a town has to read as built from a distance at which its shape does not.
      Terrain(61, "TIMBER", SurfaceSlot.Neutral, 0.44f, 0.31f, 0.19f),
      Terrain(62, "PLASTER", SurfaceSlot.Neutral, 0.86f, 0.82f, 0.73f),
      Terrain(63, "THATCH", SurfaceSlot.Neutral, 0.72f, 0.60f, 0.30f),
      Terrain(64, "ROOF_TILE", SurfaceSlot.Neutral, 0.55f, 0.26f, 0.20f),

      // Rubble and cobblestone are the exception: both are broken stone, so rock's grain is the right one and
      // only the colour differs. They are worked materials that happen to be made of the unworked one.
      Terrain(66, "RUBBLE", SurfaceSlot.Rock, 0.48f, 0.46f, 0.43f),
      Terrain(67, "COBBLESTONE", SurfaceSlot.Rock, 0.42f, 0.41f, 0.40f),

      // Ore, placed per voxel at chunk generation, three grades per metal. The grade is not decoration: it
      // decides what a broken voxel drops, so a player has to be able to see the difference between the rim
      // of a body and its middle. SMALL is barely more than tinted host rock, RICH is the metal's own colour.
      //
      // Every one of them is on the rock slot, and the grade survives that intact - because it was never a
      // difference of *material*. An ore voxel is host rock with metal in it, so what tells the grades apart is
      // exactly the colour, drawn over the same fracture and grain the wall around it has. Giving ore its own
      // texture would break the thing this row block exists to protect: the seam has to read as part of the wall,
      // or a player cannot see where it stops.
      Terrain(100, "ORE_COPPER_SMALL", SurfaceSlot.Rock, 0.48f, 0.42f, 0.36f),
      Terrain(101, "ORE_COPPER_MEDIUM", SurfaceSlot.Rock, 0.60f, 0.45f, 0.30f),
      Terrain(102, "ORE_COPPER_RICH", SurfaceSlot.Rock, 0.73f, 0.46f, 0.29f),

      Terrain(103, "ORE_TIN_SMALL", SurfaceSlot.Rock, 0.47f, 0.48f, 0.49f),
      Terrain(104, "ORE_TIN_MEDIUM", SurfaceSlot.Rock, 0.61f, 0.63f, 0.66f),
      Terrain(105, "ORE_TIN_RICH", SurfaceSlot.Rock, 0.76f, 0.79f, 0.83f),

      Terrain(106, "ORE_IRON_SMALL", SurfaceSlot.Rock, 0.45f, 0.38f, 0.34f),
      Terrain(107, "ORE_IRON_MEDIUM", SurfaceSlot.Rock, 0.58f, 0.36f, 0.28f),
      Terrain(108, "ORE_IRON_RICH", SurfaceSlot.Rock, 0.70f, 0.41f, 0.31f),

      Terrain(109, "ORE_GOLD_SMALL", SurfaceSlot.Rock, 0.54f, 0.49f, 0.35f),
      Terrain(110, "ORE_GOLD_MEDIUM", SurfaceSlot.Rock, 0.77f, 0.65f, 0.31f),
      Terrain(111, "ORE_GOLD_RICH", SurfaceSlot.Rock, 0.97f, 0.81f, 0.31f),

      Terrain(112, "ORE_SILVER_SMALL", SurfaceSlot.Rock, 0.52f, 0.53f, 0.55f),
      Terrain(113, "ORE_SILVER_MEDIUM", SurfaceSlot.Rock, 0.71f, 0.73f, 0.76f),
      Terrain(114, "ORE_SILVER_RICH", SurfaceSlot.Rock, 0.89f, 0.91f, 0.94f),

      // Cold violet-cyan, which nothing else in the palette is anywhere near. The rarest thing in the ground
      // should be unmistakable the moment it appears in a shaft wall.
      Terrain(115, "ORE_MITHRANDIUM_SMALL", SurfaceSlot.Rock, 0.38f, 0.45f, 0.50f),
      Terrain(116, "ORE_MITHRANDIUM_MEDIUM", SurfaceSlot.Rock, 0.40f, 0.68f, 0.76f),
      Terrain(117, "ORE_MITHRANDIUM_RICH", SurfaceSlot.Rock, 0.48f, 0.89f, 0.94f),

      Terrain(118, "ROCK_SALT_SMALL", SurfaceSlot.Rock, 0.69f, 0.67f, 0.66f),
      Terrain(119, "ROCK_SALT_MEDIUM", SurfaceSlot.Rock, 0.83f, 0.81f, 0.80f),
      Terrain(120, "ROCK_SALT_RICH", SurfaceSlot.Rock, 0.96f, 0.94f, 0.93f),

      // Aetherite: the same ore body, dug out of corrupted rock. Violet where mithrandium is cyan, so the
      // two rare metals are told apart at a glance in a dark gallery.
      Terrain(121, "ORE_AETHERITE_SMALL", SurfaceSlot.Rock, 0.42f, 0.33f, 0.52f),
      Terrain(122, "ORE_AETHERITE_MEDIUM", SurfaceSlot.Rock, 0.58f, 0.40f, 0.78f),
      Terrain(123, "ORE_AETHERITE_RICH", SurfaceSlot.Rock, 0.74f, 0.48f, 0.98f),

      // Sulfur: acid yellow, pulled green deliberately. Gold's rich grade is a warm 0.97/0.81/0.31 and a
      // second yellow ore that read as warm would be mistaken for it in a lamplit gallery, which matters
      // because one of them is worth crossing a volcano for and the other is worth carrying home by the sack.
      Terrain(124, "ORE_SULFUR_SMALL", SurfaceSlot.Rock, 0.52f, 0.51f, 0.34f),
      Terrain(125, "ORE_SULFUR_MEDIUM", SurfaceSlot.Rock, 0.72f, 0.74f, 0.28f),
      Terrain(126, "ORE_SULFUR_RICH", SurfaceSlot.Rock, 0.88f, 0.93f, 0.26f),

      // Pyrelith: rose, which nothing else in the palette occupies. It sits in basalt beside lava, so it has
      // to be distinct from that orange as well as from aetherite's violet and mithrandium's cyan - the three
      // colours a player already reads as "rare".
      Terrain(127, "GEM_PYRELITH_SMALL", SurfaceSlot.Rock, 0.45f, 0.22f, 0.30f),
      Terrain(128, "GEM_PYRELITH_MEDIUM", SurfaceSlot.Rock, 0.72f, 0.24f, 0.42f),
      Terrain(129, "GEM_PYRELITH_RICH", SurfaceSlot.Rock, 0.96f, 0.30f, 0.52f)
    };

    /// <summary>
    /// A material on the opaque terrain surface.
    /// </summary>
    /// <remarks>
    /// Solid and sight-blocking together, which every terrain material in the server's enum currently is. A
    /// material that is one and not the other needs its own factory rather than a fourth argument here, so
    /// that the divergence is visible in the table rather than hidden in a boolean.
    /// </remarks>
    private static Block Terrain(byte id, string name, SurfaceSlot slot, float r, float g, float b) =>
      new()
      {
        Id = id, Name = name, Solid = true, Opaque = true,
        Surface = SurfaceKind.Terrain, Slot = slot, Colour = new Color(r, g, b)
      };

    /// <summary>
    /// A material on the transparent water surface: neither solid nor sight-blocking.
    /// </summary>
    /// <remarks>
    /// No slot, because the water surface is not drawn by the terrain shader and has no texture array to index
    /// into. The mesher fills the weights for every surface all the same - one code path, and the cost of eight
    /// bytes on a sheet of water is not worth a second one - so this lands on <see cref="SurfaceSlot.Neutral"/>
    /// and is ignored.
    /// </remarks>
    private static Block Fluid(byte id, string name, float r, float g, float b, float a) =>
      new()
      {
        Id = id, Name = name, Solid = false, Opaque = false,
        Surface = SurfaceKind.Water, Slot = SurfaceSlot.Neutral, Colour = new Color(r, g, b, a)
      };

    /// <summary>
    /// Molten rock: its own surface, drawn opaque and emissive. Neither solid nor sight-blocking.
    /// </summary>
    /// <remarks>
    /// Not <see cref="Fluid"/>, and the argument is the one the class remarks make about the leaf canopy: which
    /// mesh a material is drawn into is a different question from whether it obstructs. On the water surface a
    /// lava pool beside a lake would blend into one continuous sheet, with water's alpha and no glow.
    ///
    /// <para>
    /// <b>Opaque on purpose, and that is load bearing rather than aesthetic.</b> <c>ChunkBands</c> marks active
    /// cells from the occupancy byte alone, and the materialiser fills everything below the air interface to
    /// full - rock and the fluid above it alike - so a rock/fluid interface is not an occupancy change and the
    /// bed under a fluid is never meshed. (The same is already true of every lake bed in the world.) An alpha
    /// here would therefore show a hole where the pool's basin should be. Molten rock is not transparent
    /// either, so there is no tension between the two reasons.
    /// </para>
    ///
    /// <para>
    /// The glow comes from the material's emission in <c>TerrainRenderer</c>, not from the vertex colour. It
    /// lights the surface, not the scene: without SDFGI a pool does not illuminate the rock around it.
    /// </para>
    /// </remarks>
    private static Block Molten(byte id, string name, float r, float g, float b) =>
      new()
      {
        Id = id, Name = name, Solid = false, Opaque = false,
        Surface = SurfaceKind.Lava, Slot = SurfaceSlot.Neutral, Colour = new Color(r, g, b)
      };

    /// <summary>The tables for the palette this client ships with. Built once; nothing mutates them.</summary>
    public static BlockAppearance Current { get; } = From(Palette);

    /// <summary>
    /// One mask per <see cref="SurfaceKind"/>: 0xFF for the blocks that belong to it, 0 otherwise.
    /// </summary>
    /// <remarks>
    /// A table per kind rather than a named field per kind, because the two named ones did not survive the
    /// third surface: <see cref="From"/> was an <c>if terrain else water</c>, so a new kind landed silently in
    /// the water mask, which is a divergence that renders rather than throws.
    /// </remarks>
    private readonly byte[][] _mask = BuildMasks();

    private readonly bool[] _present = new bool[SurfaceKinds];

    private readonly Color[] _colour = new Color[Ids];

    /// <summary>
    /// Which texture layer each id draws from, for every id rather than every declared one.
    /// </summary>
    /// <remarks>
    /// Not left at the array's default. <see cref="SurfaceSlot.Neutral"/> happens to be zero, so this fill is
    /// belt and braces - but the two facts that make it correct live in different files, and this is the one that
    /// would fail silently. Renumbering the enum without it repaints two hundred undeclared ids at once.
    /// </remarks>
    private readonly SurfaceSlot[] _slot = BuildSlots();

    private readonly string[] _name = new string[Ids];

    /// <summary>The mesher's mask for one surface.</summary>
    public byte[] MaskOf(SurfaceKind kind) => _mask[(int)kind];

    /// <summary>Whether any block belongs to this surface, so an empty pass can be skipped.</summary>
    public bool Occupies(SurfaceKind kind) => _present[(int)kind];

    public Color ColourOf(byte blockId) => _colour[blockId];

    /// <summary>The texture layer a material draws from. Grey for one this palette does not know.</summary>
    public SurfaceSlot SlotOf(byte blockId) => _slot[blockId];

    /// <summary>The material's name, or a bare <c>#id</c> for one this palette does not know. For logging.</summary>
    public string NameOf(int blockId) =>
      blockId >= 0 && blockId < Ids && _name[blockId] != null ? _name[blockId] : $"#{blockId}";

    /// <summary>
    /// Builds the tables from an arbitrary set of materials.
    /// </summary>
    /// <remarks>
    /// Public for the sake of the tests, which drive the mesher over a handful of invented materials rather
    /// than the real two dozen. Production has exactly one instance and it is <see cref="Current"/>.
    /// </remarks>
    public static BlockAppearance From(IEnumerable<Block> blocks)
    {
      var appearance = new BlockAppearance();

      foreach (var block in blocks)
      {
        if (block.Id <= VoxelChunk.AirBlockId)
        {
          continue;
        }

        appearance._mask[(int)block.Surface][block.Id] = 0xFF;
        appearance._present[(int)block.Surface] = true;

        appearance._colour[block.Id] = block.Colour;
        appearance._slot[block.Id] = block.Slot;
        appearance._name[block.Id] = block.Name;
      }

      return appearance;
    }

    private static byte[][] BuildMasks()
    {
      var masks = new byte[SurfaceKinds][];
      for (var kind = 0; kind < masks.Length; kind++)
      {
        masks[kind] = new byte[Ids];
      }

      return masks;
    }

    private static SurfaceSlot[] BuildSlots()
    {
      var slots = new SurfaceSlot[Ids];
      System.Array.Fill(slots, SurfaceSlot.Neutral);

      return slots;
    }
  }
}
