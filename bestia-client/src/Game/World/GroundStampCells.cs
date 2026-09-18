namespace BestiaBehemothClient.Game.World
{
  /// <summary>One mark with a shape and a heading, as it arrived on the wire.</summary>
  /// <remarks>
  /// A record rather than pixels, which is what makes the fidelity a purely local choice: the same five bytes
  /// can be drawn as a perturbed normal, as a parallax dent, or later as real geometry, with no protocol or
  /// server consequence whatsoever.
  /// </remarks>
  public readonly struct GroundStamp
  {
    public GroundStamp(int cellIndex, int kind, int octant, int seed, byte strength)
    {
      CellIndex = cellIndex;
      Kind = kind;
      Octant = octant;
      Seed = seed;
      Strength = strength;
    }

    /// <summary><c>localY * chunkSize + localX</c>, the cell order every ground message uses.</summary>
    public int CellIndex { get; }

    /// <summary>What left it. See <see cref="GroundStampCells.Footprint"/>.</summary>
    public int Kind { get; }

    /// <summary>The heading it was left facing: 0 towards +x, counting towards +y, eight in a turn.</summary>
    public int Octant { get; }

    /// <summary>A shape variant, so two creatures on one trail leave two lines of tracks.</summary>
    public int Seed { get; }

    /// <summary>How strongly to draw it, which is how far through its life it is.</summary>
    public byte Strength { get; }

    public int LocalX(int chunkSize) => CellIndex % chunkSize;

    public int LocalY(int chunkSize) => CellIndex / chunkSize;
  }

  /// <summary>
  /// The packed layout one column's stamps arrive in.
  /// </summary>
  /// <remarks>
  /// <b>This is half of a wire contract</b>, and the half that cannot fail loudly: any byte string is a legal
  /// payload, so a decoder that disagrees about the layout draws plausible tracks pointing the wrong way rather
  /// than throwing. <c>ColumnStampsTest</c> pins the same layout on the server and <c>GroundStampCellsTest</c>
  /// pins it here, both from hand-written bytes rather than from each other.
  ///
  /// <para>
  /// Godot-free on purpose, for <see cref="GroundLayerCells"/>'s reason: the layout can then be tested without
  /// an engine, which is the only reason there is a test of it at all.
  /// </para>
  /// </remarks>
  public static class GroundStampCells
  {
    /// <summary>Cell index, kind and octant, seed, strength.</summary>
    public const int BytesPerStamp = 5;

    /// <summary>The <c>GroundStampKind</c> wire id for something having walked here.</summary>
    public const int Footprint = 1;

    /// <summary>How many stamps a payload holds. A trailing partial stamp is ignored rather than guessed at.</summary>
    public static int CountIn(byte[] stamps) => stamps == null ? 0 : stamps.Length / BytesPerStamp;

    /// <summary>Unpacks every stamp in a payload, oldest first so the newest draws on top.</summary>
    public static GroundStamp[] Decode(byte[] stamps)
    {
      var count = CountIn(stamps);
      var decoded = new GroundStamp[count];

      for (var i = 0; i < count; i++)
      {
        decoded[i] = At(stamps, i);
      }

      return decoded;
    }

    /// <summary>Unpacks one stamp. See <c>ChunkGroundStampEncoding</c> for the byte layout this mirrors.</summary>
    public static GroundStamp At(byte[] stamps, int index)
    {
      var at = index * BytesPerStamp;

      return new GroundStamp(
        cellIndex: stamps[at] | stamps[at + 1] << 8,
        kind: stamps[at + 2] >> 4 & 0x0F,
        octant: stamps[at + 2] & 0x07,
        seed: stamps[at + 3],
        strength: stamps[at + 4]);
    }
  }
}
