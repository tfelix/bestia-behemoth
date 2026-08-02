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
  /// It used to come from <see cref="Block.Solid"/>, and <c>LEAVES</c> is why it no longer does. Solidity is
  /// the server's word for "does this obstruct" - it decides pathing, spawn heights and line of sight - and a
  /// canopy is deliberately not solid so that agents walk under it. Which mesh a material is drawn into is a
  /// different question with a different answer: leaves are opaque green and belong on the terrain surface,
  /// not blended into the transparent water one, where a tree beside a lake would have merged with it.
  /// </para>
  ///
  /// <para>
  /// Vertex colour is the interim answer to texturing. It puts recognisable terrain on screen for the cost of
  /// a lookup table, and it is deliberately not the destination: the shape of the data - one material id per
  /// vertex - is the same shape a <c>Texture2DArray</c> blended in the shader wants, so replacing
  /// <see cref="ColourOf"/> with a layer index in <c>ARRAY_CUSTOM0</c> changes this file and the shader and
  /// nothing else.
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
      Water
    }

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
      Terrain(2, "ICE", 0.78f, 0.88f, 0.93f),

      // Basement.
      Terrain(10, "GRANITE", 0.60f, 0.56f, 0.55f),
      Terrain(11, "BASALT", 0.26f, 0.26f, 0.28f),

      // Sedimentary cover.
      Terrain(20, "LIMESTONE", 0.78f, 0.76f, 0.68f),
      Terrain(21, "SANDSTONE", 0.76f, 0.62f, 0.42f),
      Terrain(22, "SHALE", 0.34f, 0.35f, 0.36f),
      Terrain(23, "CONGLOMERATE", 0.57f, 0.51f, 0.45f),

      // Unconsolidated.
      Terrain(30, "GRAVEL", 0.52f, 0.50f, 0.48f),
      Terrain(31, "SAND", 0.85f, 0.76f, 0.55f),
      Terrain(32, "CLAY", 0.60f, 0.45f, 0.36f),
      Terrain(33, "DIRT", 0.38f, 0.28f, 0.19f),
      Terrain(34, "PEAT", 0.22f, 0.17f, 0.13f),
      Terrain(35, "PERMAFROST", 0.55f, 0.57f, 0.60f),

      // Surface cover.
      Terrain(40, "GRASS", 0.28f, 0.45f, 0.19f),
      Terrain(41, "SNOW", 0.92f, 0.94f, 0.96f),

      // Vegetation, scattered per column at chunk generation. LEAVES is the first material drawn on the
      // terrain surface without being solid - see Foliage.
      Terrain(45, "LOG", 0.33f, 0.24f, 0.15f),
      Foliage(46, "LEAVES", 0.19f, 0.36f, 0.15f),

      // Bridge decking and other worked structure.
      Terrain(60, "MASONRY", 0.62f, 0.60f, 0.56f),

      // Buildings and streets, added with the server's step 8. Deliberately more saturated than the natural
      // materials they stand on: a town has to read as built from a distance at which its shape does not.
      Terrain(61, "TIMBER", 0.44f, 0.31f, 0.19f),
      Terrain(62, "PLASTER", 0.86f, 0.82f, 0.73f),
      Terrain(63, "THATCH", 0.72f, 0.60f, 0.30f),
      Terrain(64, "ROOF_TILE", 0.55f, 0.26f, 0.20f),
      Terrain(66, "RUBBLE", 0.48f, 0.46f, 0.43f),
      Terrain(67, "COBBLESTONE", 0.42f, 0.41f, 0.40f),

      // Ore, placed per voxel at chunk generation, three grades per metal. The grade is not decoration: it
      // decides what a broken voxel drops, so a player has to be able to see the difference between the rim
      // of a body and its middle. SMALL is barely more than tinted host rock, RICH is the metal's own colour.
      Terrain(100, "ORE_COPPER_SMALL", 0.48f, 0.42f, 0.36f),
      Terrain(101, "ORE_COPPER_MEDIUM", 0.60f, 0.45f, 0.30f),
      Terrain(102, "ORE_COPPER_RICH", 0.73f, 0.46f, 0.29f),

      Terrain(103, "ORE_TIN_SMALL", 0.47f, 0.48f, 0.49f),
      Terrain(104, "ORE_TIN_MEDIUM", 0.61f, 0.63f, 0.66f),
      Terrain(105, "ORE_TIN_RICH", 0.76f, 0.79f, 0.83f),

      Terrain(106, "ORE_IRON_SMALL", 0.45f, 0.38f, 0.34f),
      Terrain(107, "ORE_IRON_MEDIUM", 0.58f, 0.36f, 0.28f),
      Terrain(108, "ORE_IRON_RICH", 0.70f, 0.41f, 0.31f),

      Terrain(109, "ORE_GOLD_SMALL", 0.54f, 0.49f, 0.35f),
      Terrain(110, "ORE_GOLD_MEDIUM", 0.77f, 0.65f, 0.31f),
      Terrain(111, "ORE_GOLD_RICH", 0.97f, 0.81f, 0.31f),

      Terrain(112, "ORE_SILVER_SMALL", 0.52f, 0.53f, 0.55f),
      Terrain(113, "ORE_SILVER_MEDIUM", 0.71f, 0.73f, 0.76f),
      Terrain(114, "ORE_SILVER_RICH", 0.89f, 0.91f, 0.94f),

      // Cold violet-cyan, which nothing else in the palette is anywhere near. The rarest thing in the ground
      // should be unmistakable the moment it appears in a shaft wall.
      Terrain(115, "ORE_MITHRANDIUM_SMALL", 0.38f, 0.45f, 0.50f),
      Terrain(116, "ORE_MITHRANDIUM_MEDIUM", 0.40f, 0.68f, 0.76f),
      Terrain(117, "ORE_MITHRANDIUM_RICH", 0.48f, 0.89f, 0.94f),

      Terrain(118, "ROCK_SALT_SMALL", 0.69f, 0.67f, 0.66f),
      Terrain(119, "ROCK_SALT_MEDIUM", 0.83f, 0.81f, 0.80f),
      Terrain(120, "ROCK_SALT_RICH", 0.96f, 0.94f, 0.93f)
    };

    /// <summary>
    /// A material on the opaque terrain surface.
    /// </summary>
    /// <remarks>
    /// Solid and sight-blocking together, which every terrain material in the server's enum currently is. A
    /// material that is one and not the other needs its own factory rather than a fourth argument here, so
    /// that the divergence is visible in the table rather than hidden in a boolean.
    /// </remarks>
    private static Block Terrain(byte id, string name, float r, float g, float b) =>
      new()
      {
        Id = id, Name = name, Solid = true, Opaque = true,
        Surface = SurfaceKind.Terrain, Colour = new Color(r, g, b)
      };

    /// <summary>A material on the transparent water surface: neither solid nor sight-blocking.</summary>
    private static Block Fluid(byte id, string name, float r, float g, float b, float a) =>
      new()
      {
        Id = id, Name = name, Solid = false, Opaque = false,
        Surface = SurfaceKind.Water, Colour = new Color(r, g, b, a)
      };

    /// <summary>
    /// Leaf canopy: drawn on the opaque terrain surface, but neither solid nor sight-blocking.
    /// </summary>
    /// <remarks>
    /// Its own factory rather than a fourth argument to <see cref="Terrain"/>, exactly as that method's
    /// remarks demand: the divergence between "obstructs" and "is drawn opaque" is the interesting thing
    /// about this material, and it belongs in the table where it can be read, not inside a boolean.
    ///
    /// <para>
    /// The server also gives it a fractional <c>opacity</c>, so a sight line through a canopy attenuates
    /// rather than stopping dead. Nothing here needs it: line of sight is resolved server side.
    /// </para>
    /// </remarks>
    private static Block Foliage(byte id, string name, float r, float g, float b) =>
      new()
      {
        Id = id, Name = name, Solid = false, Opaque = false,
        Surface = SurfaceKind.Terrain, Colour = new Color(r, g, b)
      };

    /// <summary>The tables for the palette this client ships with. Built once; nothing mutates them.</summary>
    public static BlockAppearance Current { get; } = From(Palette);

    /// <summary>0xFF for blocks that belong to the opaque terrain surface, 0 otherwise.</summary>
    public byte[] TerrainMask { get; } = new byte[Ids];

    /// <summary>0xFF for blocks that belong to the transparent water surface, 0 otherwise.</summary>
    public byte[] WaterMask { get; } = new byte[Ids];

    private readonly Color[] _colour = new Color[Ids];

    private readonly string[] _name = new string[Ids];

    /// <summary>Whether any block in the palette belongs to the water surface, so an empty pass can be skipped.</summary>
    public bool HasWater { get; private set; }

    public Color ColourOf(byte blockId) => _colour[blockId];

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

        if (block.Surface == SurfaceKind.Terrain)
        {
          appearance.TerrainMask[block.Id] = 0xFF;
        }
        else
        {
          appearance.WaterMask[block.Id] = 0xFF;
          appearance.HasWater = true;
        }

        appearance._colour[block.Id] = block.Colour;
        appearance._name[block.Id] = block.Name;
      }

      return appearance;
    }
  }
}
