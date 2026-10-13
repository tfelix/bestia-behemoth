using System.IO;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The cell layouts one ground layer arrives in: nibbles for what is drawn as sent, sparse exact levels for
  /// what the client keeps adding to.
  /// </summary>
  /// <remarks>
  /// <b>This is half of a wire contract</b>, and the half that cannot fail loudly: any byte string is a legal
  /// payload, so a decoder that disagrees about the order draws plausible wear in the wrong places rather than
  /// throwing. <c>ColumnLevelsTest</c> pins the same layout on the server and
  /// <c>GroundLayerCellsTest</c> pins it here, both from hand-written bytes rather than from each other.
  ///
  /// <para>
  /// Godot-free on purpose, so the layout can be tested without an engine - which is the only reason there is
  /// a test of it at all. <see cref="Bnet.Message.Map.ChunkGroundLayersSMSG"/> and
  /// <see cref="GroundMarkTexture"/> both read through here rather than each unpacking their own nibbles.
  /// </para>
  /// </remarks>
  public static class GroundLayerCells
  {
    /// <summary>The strongest level a cell can carry, being all four bits.</summary>
    public const int MaxLevel = 15;

    /// <summary>How many bytes a column's cells occupy: two cells to a byte.</summary>
    public static int ByteLength(int chunkSize) => (chunkSize * chunkSize + 1) / 2;

    /// <summary>Bytes per cell of <c>LEVELS_SPARSE_V1</c>: index u16 little endian, then level u8.</summary>
    public const int BytesPerSparseCell = 3;

    /// <summary>The proto's <c>ChunkGroundLayerEncoding</c> values this build reads. Mirror its values, not its ordinals.</summary>
    public const uint NibbleV1 = 1;

    public const uint LevelsSparseV1 = 2;

    /// <summary>
    /// Reads one layer's entry from a layers message.
    /// </summary>
    /// <remarks>
    /// No cells means the layer is retired, whatever the encoding says. Otherwise the encoding must suit the
    /// layer: worn ground and blood need their exact levels, and a nibble payload would seed them from a lossy
    /// copy that crosses the visible threshold at the wrong footfall.
    /// </remarks>
    /// <exception cref="InvalidDataException">
    /// on an encoding this build does not know or that does not suit the layer, or cells of the wrong length.
    /// </exception>
    public static GroundLayerEntry ReadEntry(
      GroundLayers.Id layer, uint encoding, byte[] cells, long lastDecayedSecond, int chunkSize)
    {
      if (cells.Length == 0)
      {
        return GroundLayerEntry.Retire(layer);
      }

      var graded = layer == GroundLayers.Id.Worn || layer == GroundLayers.Id.Bloodied;

      if (encoding == LevelsSparseV1 && graded)
      {
        return GroundLayerEntry.FromLevels(layer, DecodeSparse(cells, chunkSize), lastDecayedSecond);
      }

      if (encoding == NibbleV1 && !graded)
      {
        var expected = ByteLength(chunkSize);
        if (cells.Length != expected)
        {
          throw new InvalidDataException($"{layer} cells are {cells.Length} B, expected {expected} B");
        }

        return GroundLayerEntry.FromNibbles(layer, cells);
      }

      throw new InvalidDataException($"ground layer encoding {encoding} is not one this build can read for {layer}");
    }

    /// <summary>
    /// Unpacks <c>LEVELS_SPARSE_V1</c> into one exact level per cell, <c>localY * chunkSize + localX</c>.
    /// </summary>
    /// <remarks>
    /// Cells the payload does not name are zero. Strict about length and index, because any byte string is a
    /// legal payload and a decoder that disagrees about the layout draws wear in the wrong places.
    /// </remarks>
    /// <exception cref="InvalidDataException">
    /// on a length that is not a whole number of cells, or an index outside the column.
    /// </exception>
    public static byte[] DecodeSparse(byte[] payload, int chunkSize)
    {
      if (payload.Length % BytesPerSparseCell != 0)
      {
        throw new InvalidDataException(
          $"sparse levels are {payload.Length} B, not a whole number of {BytesPerSparseCell} B cells");
      }

      var levels = new byte[chunkSize * chunkSize];

      for (var at = 0; at < payload.Length; at += BytesPerSparseCell)
      {
        var index = payload[at] | payload[at + 1] << 8;
        if (index >= levels.Length)
        {
          throw new InvalidDataException($"sparse level at cell {index} is outside a {chunkSize}-wide column");
        }

        levels[index] = payload[at + 2];
      }

      return levels;
    }

    /// <summary>
    /// How strongly this layer marks cell <c>(localX, localY)</c>, 0 to <see cref="MaxLevel"/>.
    /// </summary>
    /// <remarks>
    /// <c>localY * chunkSize + localX</c>, that index's nibble in byte <c>index / 2</c> - low nibble for an
    /// even index, high for an odd one. The same cell order <c>ColumnMask</c> and <c>ColumnSummary</c> use, so
    /// a reader who knows one knows the others.
    /// </remarks>
    /// <returns>0 for absent cells, which is what a layer this column has nothing of looks like.</returns>
    public static int LevelAt(byte[] cells, int chunkSize, int localX, int localY)
    {
      if (cells == null)
      {
        return 0;
      }

      var index = localY * chunkSize + localX;
      var packed = cells[index >> 1];

      return (index & 1) == 0 ? packed & 0x0F : packed >> 4 & 0x0F;
    }
  }

  /// <summary>
  /// What a layers message says about one layer: drawn nibbles, exact levels with their decay clock, or that
  /// the layer is gone.
  /// </summary>
  /// <remarks>
  /// Replaces the layer it names and touches no other, so a message can speak about one layer alone.
  /// </remarks>
  public sealed class GroundLayerEntry
  {
    private GroundLayerEntry(GroundLayers.Id layer, byte[] nibbles, byte[] levels, long lastDecayedSecond)
    {
      Layer = layer;
      Nibbles = nibbles;
      Levels = levels;
      LastDecayedSecond = lastDecayedSecond;
    }

    public GroundLayers.Id Layer { get; }

    /// <summary><c>NIBBLE_V1</c> cells, drawn as sent. Null unless this entry carries them.</summary>
    public byte[] Nibbles { get; }

    /// <summary>One exact level per cell, from <c>LEVELS_SPARSE_V1</c>. Null unless this entry carries them.</summary>
    public byte[] Levels { get; }

    /// <summary>The Bestia second the levels have already faded up to. Zero for nibbles, which do not decay.</summary>
    public long LastDecayedSecond { get; }

    /// <summary>Whether the layer is to be dropped, which is what an entry with no cells means.</summary>
    public bool Retires => Nibbles == null && Levels == null;

    public static GroundLayerEntry Retire(GroundLayers.Id layer) => new(layer, null, null, 0);

    public static GroundLayerEntry FromNibbles(GroundLayers.Id layer, byte[] nibbles) =>
      new(layer, nibbles, null, 0);

    public static GroundLayerEntry FromLevels(GroundLayers.Id layer, byte[] levels, long lastDecayedSecond) =>
      new(layer, null, levels, lastDecayedSecond);
  }
}
