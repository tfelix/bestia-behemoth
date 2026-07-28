using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// Turns the server's block palette into the two things the mesher needs: which blocks belong to which
  /// surface, and what colour each one is.
  /// </summary>
  /// <remarks>
  /// Both are lookup tables indexed directly by block id, because they are read in the mesher's innermost loop
  /// and ids are only ever a byte on the wire. Sparse ids waste a couple of hundred entries, which is the whole
  /// cost of not having a dictionary lookup per voxel.
  ///
  /// <para>
  /// Membership comes from the palette's own <c>solid</c> flag rather than from hardcoded ids, which is the
  /// point of the palette being sent at all: <c>ICE</c> is solid and belongs to the terrain surface, <c>WATER</c>
  /// is not and gets its own transparent one, and a new material on the server picks the right surface without a
  /// client release.
  /// </para>
  ///
  /// <para>
  /// Vertex colour is the interim answer to texturing. It puts recognisable terrain on screen for the cost of a
  /// lookup table, and it is deliberately not the destination: the shape of the data - one material id per vertex
  /// - is the same shape a <c>Texture2DArray</c> blended in the shader wants, so replacing
  /// <see cref="ColourOf"/> with a layer index in <c>ARRAY_CUSTOM0</c> changes this file and the shader and
  /// nothing else.
  /// </para>
  /// </remarks>
  public sealed class BlockAppearance
  {
    /// <summary>Block ids are a byte on the wire, so every table covers the whole range.</summary>
    public const int Ids = 256;

    /// <summary>0xFF for blocks that belong to the opaque terrain surface, 0 otherwise.</summary>
    public byte[] TerrainMask { get; } = new byte[Ids];

    /// <summary>0xFF for blocks that belong to the transparent water surface, 0 otherwise.</summary>
    public byte[] WaterMask { get; } = new byte[Ids];

    private readonly Color[] _colour = new Color[Ids];

    /// <summary>Whether any block in the palette belongs to the water surface, so an empty pass can be skipped.</summary>
    public bool HasWater { get; private set; }

    public Color ColourOf(byte blockId) => _colour[blockId];

    /// <summary>Builds the tables from a received palette.</summary>
    public static BlockAppearance From(BlockPaletteSMSG palette) => From(palette?.ById.Values);

    /// <summary>
    /// Builds the tables from palette entries.
    /// </summary>
    /// <remarks>
    /// Takes the entries rather than the message, because <see cref="BlockPaletteSMSG"/> is a <c>GodotObject</c>
    /// and cannot be constructed without the engine running - which would put the whole appearance and meshing
    /// path out of reach of anything that is not a live game.
    ///
    /// <para>
    /// No entries yields a usable fallback rather than a failure: block zero is air by definition of the wire
    /// format, so treating everything else as opaque terrain draws the world in placeholder grey instead of
    /// drawing nothing. The palette arrives alongside the world info and before any chunk, so this should not
    /// happen; it costs nothing to not depend on that.
    /// </para>
    /// </remarks>
    public static BlockAppearance From(IEnumerable<BlockPaletteSMSG.Entry> entries)
    {
      var appearance = new BlockAppearance();

      if (entries == null)
      {
        for (var id = 1; id < Ids; id++)
        {
          appearance.TerrainMask[id] = 0xFF;
          appearance._colour[id] = Fallback;
        }

        return appearance;
      }

      foreach (var entry in entries)
      {
        if (entry.Id <= VoxelChunk.AirBlockId || entry.Id >= Ids)
        {
          continue;
        }

        if (entry.Solid)
        {
          appearance.TerrainMask[entry.Id] = 0xFF;
        }
        else
        {
          appearance.WaterMask[entry.Id] = 0xFF;
          appearance.HasWater = true;
        }

        appearance._colour[entry.Id] = ColourFor(entry.Name);
      }

      return appearance;
    }

    private static readonly Color Fallback = new(0.55f, 0.53f, 0.50f);

    /// <summary>
    /// Colours by material name, so the palette stays the server's to define.
    /// </summary>
    /// <remarks>
    /// Keyed on the name rather than the id because a name is what a human wrote and an id is an accident of
    /// grouping. A material this table has not heard of falls back to a colour derived from its name, which keeps
    /// distinct unknown materials distinguishable on screen instead of collapsing them all into one grey.
    /// </remarks>
    private static readonly Dictionary<string, Color> ByName = new()
    {
      ["WATER"] = new Color(0.16f, 0.35f, 0.52f, 0.72f),
      ["ICE"] = new Color(0.78f, 0.88f, 0.93f),

      ["GRANITE"] = new Color(0.60f, 0.56f, 0.55f),
      ["BASALT"] = new Color(0.26f, 0.26f, 0.28f),

      ["LIMESTONE"] = new Color(0.78f, 0.76f, 0.68f),
      ["SANDSTONE"] = new Color(0.76f, 0.62f, 0.42f),
      ["SHALE"] = new Color(0.34f, 0.35f, 0.36f),
      ["CONGLOMERATE"] = new Color(0.57f, 0.51f, 0.45f),

      ["GRAVEL"] = new Color(0.52f, 0.50f, 0.48f),
      ["SAND"] = new Color(0.85f, 0.76f, 0.55f),
      ["CLAY"] = new Color(0.60f, 0.45f, 0.36f),
      ["DIRT"] = new Color(0.38f, 0.28f, 0.19f),
      ["PEAT"] = new Color(0.22f, 0.17f, 0.13f),
      ["PERMAFROST"] = new Color(0.55f, 0.57f, 0.60f),

      ["GRASS"] = new Color(0.28f, 0.45f, 0.19f),
      ["SNOW"] = new Color(0.92f, 0.94f, 0.96f),

      ["ORE_COPPER"] = new Color(0.45f, 0.55f, 0.45f),
      ["ORE_TIN"] = new Color(0.58f, 0.58f, 0.62f),
      ["ORE_IRON"] = new Color(0.53f, 0.38f, 0.30f),
      ["ORE_GOLD"] = new Color(0.78f, 0.66f, 0.28f),
      ["ORE_SILVER"] = new Color(0.72f, 0.74f, 0.76f),
      ["COAL_SEAM"] = new Color(0.13f, 0.13f, 0.14f),
      ["ROCK_SALT"] = new Color(0.86f, 0.84f, 0.82f),

      ["MASONRY"] = new Color(0.62f, 0.60f, 0.56f)
    };

    private static Color ColourFor(string name)
    {
      if (ByName.TryGetValue(name, out var known))
      {
        return known;
      }

      // Deterministic per name, and kept away from both extremes of value so it reads as a material under the
      // scene's directional light rather than as a black or blown-out patch.
      var hash = name.GetHashCode();

      return Color.FromHsv(
        ((hash >>> 8) & 0xFF) / 255.0f,
        0.25f + ((hash >>> 16) & 0x3F) / 255.0f,
        0.45f + ((hash >>> 24) & 0x3F) / 255.0f);
    }
  }
}
