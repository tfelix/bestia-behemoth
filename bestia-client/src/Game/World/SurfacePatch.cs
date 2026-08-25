using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The visible surface of a square of ground: what the client draws past the range it is sent real chunks.
  /// </summary>
  /// <remarks>
  /// Four planes, one value per sample on a 65x65 grid. Height, standing water, material and canopy density -
  /// between them everything a distant hillside needs.
  ///
  /// <para>
  /// No caves, no overhangs, no ore, no props. A patch is a heightfield, so it cannot express a hole, which is
  /// exactly why it is cheap and exactly why it cannot replace the full-detail ring: you can look at a cave
  /// mouth from a kilometre away, but you cannot walk into one you were only sent a patch of.
  /// </para>
  ///
  /// <para>
  /// It also does not move. A patch comes from the server's heightfield, which no player edit can touch, so it
  /// carries no revision and one held is one that stays correct for the life of the world. That is what makes
  /// <see cref="PatchDiskCache"/> safe where <see cref="ClientChunkStore"/> deliberately is not.
  /// </para>
  /// </remarks>
  public sealed class SurfacePatch
  {
    /// <summary>The dry sentinel. A NaN, so it must be tested with <see cref="HasWaterAt"/> and never with <c>==</c>.</summary>
    public const float NoWater = float.NaN;

    public PatchKey Key { get; }

    /// <summary>Terrain surface elevation per sample, in metres. Defined everywhere, sea floor included.</summary>
    public float[] Height { get; }

    /// <summary>Standing water surface per sample in metres, or <see cref="NoWater"/> where the ground is dry.</summary>
    public float[] Water { get; }

    /// <summary>Block id of the topmost material per sample, indexing the same palette a chunk's voxels do.</summary>
    public byte[] Block { get; }

    /// <summary>Canopy cover per sample as 0..255, for scattering distant vegetation without streaming any.</summary>
    public byte[] Canopy { get; }

    public SurfacePatch(PatchKey key, float[] height, float[] water, byte[] block, byte[] canopy)
    {
      Key = key;
      Height = height;
      Water = water;
      Block = block;
      Canopy = canopy;
    }

    public float HeightAt(int i, int j) => Height[PatchGrid.Index(i, j)];

    public float WaterAt(int i, int j) => Water[PatchGrid.Index(i, j)];

    public bool HasWaterAt(int i, int j) => !float.IsNaN(Water[PatchGrid.Index(i, j)]);

    public byte BlockAt(int i, int j) => Block[PatchGrid.Index(i, j)];

    public byte CanopyAt(int i, int j) => Canopy[PatchGrid.Index(i, j)];

    public override string ToString() => $"SurfacePatch[{Key}]";
  }

  /// <summary>
  /// Reads the wire form of a patch. Mirrors the server's <c>SurfacePatchCodec</c>.
  /// </summary>
  /// <remarks>
  /// Four fixed-width planes after a small header, big-endian. Planes rather than one interleaved stream
  /// because the four values have nothing in common and every fourth byte would break the other three's runs -
  /// the same argument <see cref="RleCodec"/> makes for keeping its two streams apart.
  ///
  /// <para>
  /// Heights are quarter-metres above a floor stored once per patch. A quarter of a metre is a twelfth of a
  /// sample spacing at the finest level, far below what a slope four metres wide can show, and storing them
  /// relative keeps neighbouring values numerically close - which is what the deflate on top is relying on.
  /// </para>
  /// </remarks>
  public static class SurfacePatchCodec
  {
    /// <summary>Named on the wire as <c>SURFACE_PATCH_ENCODING_PLANES_V1</c>, so it moves only when the bytes do.</summary>
    public const int Version = 1;

    private const float HeightStep = 0.25f;

    private const int HeaderBytes = 1 + 1 + 4 + 4 + 4;
    private const int BytesPerSample = 6;

    /// <summary>Exact size of a payload, so a truncated one is caught before it is read rather than after.</summary>
    public static int EncodedSize => HeaderBytes + PatchGrid.SampleCount * BytesPerSample;

    public static SurfacePatch Decode(byte[] bytes)
    {
      if (bytes == null || bytes.Length != EncodedSize)
      {
        throw new FormatException($"a patch payload is {EncodedSize} bytes, got {bytes?.Length ?? 0}");
      }

      var at = 0;
      int version = bytes[at++];
      if (version != Version)
      {
        throw new FormatException($"patch encoded with surface-patch version {version}, this build reads {Version}");
      }

      int level = bytes[at++];
      var x = ReadInt(bytes, ref at);
      var y = ReadInt(bytes, ref at);
      var floorSteps = ReadInt(bytes, ref at);

      var count = PatchGrid.SampleCount;
      var height = new float[count];
      var water = new float[count];
      var block = new byte[count];
      var canopy = new byte[count];

      for (var i = 0; i < count; i++)
      {
        height[i] = Metres(ReadShort(bytes, ref at), floorSteps);
      }

      for (var i = 0; i < count; i++)
      {
        // Zero is the dry marker, which is why heights in this plane are stored from one rather than zero.
        var raw = ReadShort(bytes, ref at);
        water[i] = raw == 0 ? SurfacePatch.NoWater : Metres(raw - 1, floorSteps);
      }

      Array.Copy(bytes, at, block, 0, count);
      at += count;
      Array.Copy(bytes, at, canopy, 0, count);

      return new SurfacePatch(new PatchKey(level, x, y), height, water, block, canopy);
    }

    private static float Metres(int steps, int floorSteps) => (steps + floorSteps) * HeightStep;

    private static int ReadShort(byte[] bytes, ref int at)
    {
      var value = (bytes[at] << 8) | bytes[at + 1];
      at += 2;
      return value;
    }

    private static int ReadInt(byte[] bytes, ref int at)
    {
      var value = (bytes[at] << 24) | (bytes[at + 1] << 16) | (bytes[at + 2] << 8) | bytes[at + 3];
      at += 4;
      return value;
    }
  }
}
