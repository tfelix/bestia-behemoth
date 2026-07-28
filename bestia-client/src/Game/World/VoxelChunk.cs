using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// A decoded chunk: <c>Size x Size x Height</c> voxels, each a material id and how much of the voxel that
  /// material fills.
  /// </summary>
  /// <remarks>
  /// The port of the server's <c>VoxelChunk</c>, and it has to agree with it on three things or the payload is
  /// misread rather than rejected: the vertical axis is contiguous, materials and occupancy are two parallel
  /// arrays rather than interleaved pairs, and air is the only material with occupancy zero.
  ///
  /// <para>
  /// Occupancy is not decoration. A surface at 40.3 m is stored as the voxel spanning 40 to 41 being thirty
  /// percent full, so a renderer can reconstruct the original height to about a fifth of a centimetre.
  /// Rounding it away would replace the whole point of the vector-feature pipeline with metre stair-steps.
  /// </para>
  /// </remarks>
  public sealed class VoxelChunk
  {
    public const int AirBlockId = 0;

    public int ChunkX { get; }
    public int ChunkY { get; }
    public int ChunkZ { get; }

    public int Size { get; }
    public int Height { get; }

    /// <summary>Raw block ids, indexed by <see cref="Index"/>. Unsigned, so read through <see cref="BlockAt"/>.</summary>
    public byte[] Blocks { get; }

    /// <summary>How much of each voxel its material fills, 0..255.</summary>
    public byte[] Occupancy { get; }

    public int Volume => Blocks.Length;

    public VoxelChunk(int chunkX, int chunkY, int chunkZ, int size, int height, byte[] blocks, byte[] occupancy)
    {
      if (size <= 0 || height <= 0)
      {
        throw new ArgumentException($"Chunk dimensions must be positive, was {size}x{size}x{height}");
      }

      var volume = size * size * height;
      if (blocks.Length != volume)
      {
        throw new ArgumentException($"A {size}x{size}x{height} chunk needs {volume} blocks, got {blocks.Length}");
      }

      if (occupancy.Length != blocks.Length)
      {
        throw new ArgumentException($"Occupancy has {occupancy.Length} entries for {blocks.Length} blocks");
      }

      ChunkX = chunkX;
      ChunkY = chunkY;
      ChunkZ = chunkZ;
      Size = size;
      Height = height;
      Blocks = blocks;
      Occupancy = occupancy;
    }

    /// <summary>Start of the voxel column at (<paramref name="localX"/>, <paramref name="localY"/>).</summary>
    public int ColumnOffset(int localX, int localY) => (localY * Size + localX) * Height;

    public int Index(int localX, int localY, int localZ) => ColumnOffset(localX, localY) + localZ;

    public int BlockAt(int localX, int localY, int localZ) => Blocks[Index(localX, localY, localZ)];

    public int OccupancyAt(int localX, int localY, int localZ) => Occupancy[Index(localX, localY, localZ)];

    /// <summary>
    /// Applies one edit from a chunk patch, by the raw voxel index the server sent.
    /// </summary>
    /// <remarks>
    /// Material and occupancy are written together because the invariant is about the pair. Writing one and
    /// then the other would leave the chunk momentarily inconsistent, which matters here because the renderer
    /// reads it on a different frame boundary from the network thread that fills it.
    /// </remarks>
    public void ApplyEdit(int index, byte blockId, byte occupancy)
    {
      if (index < 0 || index >= Blocks.Length)
      {
        throw new ArgumentOutOfRangeException(
          nameof(index), $"Voxel index {index} is outside a chunk of {Blocks.Length} voxels");
      }

      if ((blockId == AirBlockId) != (occupancy == 0))
      {
        throw new ArgumentException(
          $"Air must have occupancy 0 and everything else must not; got block {blockId} at {occupancy}");
      }

      Blocks[index] = blockId;
      Occupancy[index] = occupancy;
    }

    /// <summary>
    /// Height of the top solid surface of a column, in voxels above the chunk floor, or -1 if the column is
    /// empty. Continuous rather than an integer, using the top voxel's occupancy.
    /// </summary>
    public double SurfaceHeightAt(int localX, int localY)
    {
      var offset = ColumnOffset(localX, localY);

      for (var localZ = Height - 1; localZ >= 0; localZ--)
      {
        var i = offset + localZ;
        if (Blocks[i] == AirBlockId)
        {
          continue;
        }

        return localZ + Occupancy[i] / 255.0;
      }

      return -1.0;
    }

    /// <summary>Throws if the air-implies-empty invariant is broken anywhere. See <see cref="RleCodec"/>.</summary>
    public void Validate()
    {
      for (var i = 0; i < Blocks.Length; i++)
      {
        var isAir = Blocks[i] == AirBlockId;
        var isEmpty = Occupancy[i] == 0;

        if (isAir != isEmpty)
        {
          throw new InvalidOperationException(
            $"Chunk ({ChunkX},{ChunkY},{ChunkZ}) voxel {i} is block {Blocks[i]} at occupancy {Occupancy[i]}; " +
            "air must be empty and everything else must not be");
        }
      }
    }
  }
}
