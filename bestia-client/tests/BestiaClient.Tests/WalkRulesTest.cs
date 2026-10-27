using System;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client's step rule against the server's: the cases <c>WalkableTile</c> and <c>ChunkWalkQuery.canStep</c>
  /// decide, answered the same way.
  /// </summary>
  public class WalkRulesTest
  {
    private const int Size = TerrainFixtures.Size;
    private const int Height = TerrainFixtures.Height;

    private const byte Granite = 4;
    private const byte Water = 1;
    private const byte Lava = 3;

    /// <summary>
    /// A chunk whose ground column at local x is solid granite up to <paramref name="ground"/>(x) exclusive, so its
    /// surface is exactly that height, with <paramref name="fluid"/> voxels of <paramref name="fluidBlock"/> on top.
    /// </summary>
    private static VoxelChunk Chunk(Func<int, int> ground, Func<int, int> fluid = null, byte fluidBlock = Water)
    {
      var blocks = new byte[Size * Size * Height];
      var occupancy = new byte[Size * Size * Height];

      for (var y = 0; y < Size; y++)
      {
        for (var x = 0; x < Size; x++)
        {
          var offset = (y * Size + x) * Height;
          var top = ground(x);
          for (var z = 0; z < top; z++)
          {
            blocks[offset + z] = Granite;
            occupancy[offset + z] = 255;
          }

          var depth = fluid?.Invoke(x) ?? 0;
          for (var z = top; z < top + depth; z++)
          {
            blocks[offset + z] = fluidBlock;
            occupancy[offset + z] = 255;
          }
        }
      }

      return new VoxelChunk(0, 0, 0, Size, Height, blocks, occupancy);
    }

    private static WalkRules.Step Check(VoxelChunk chunk, long fromX, double fromZ, long toX, out double landing)
    {
      var source = new FakeChunkSource();
      if (chunk != null)
      {
        source.Put(chunk);
      }

      return WalkRules.Check(source, ChunkWrap.None, Size, Height, fromX, 5, fromZ, toX, 5, out landing);
    }

    [Fact]
    public void FlatGroundIsWalkedOntoItsOwnSurface()
    {
      var step = Check(Chunk(_ => 40), 5, 40, 6, out var landing);

      Assert.Equal(WalkRules.Step.Allowed, step);
      Assert.Equal(40.0, landing, 6);
    }

    [Fact]
    public void ARiseWithinTheSlopeLimitIsAllowed()
    {
      // One voxel over one metre is 45°, under the server's sixty.
      Assert.Equal(WalkRules.Step.Allowed, Check(Chunk(x => x < 6 ? 40 : 41), 5, 40, 6, out var landing));
      Assert.Equal(41.0, landing, 6);
    }

    [Fact]
    public void ACliffIsRefused()
    {
      // Two voxels over one metre is 63°, over it.
      Assert.Equal(WalkRules.Step.Refused, Check(Chunk(x => x < 6 ? 40 : 42), 5, 40, 6, out _));
      Assert.Equal(WalkRules.Step.Refused, Check(Chunk(x => x < 6 ? 42 : 40), 5, 42, 6, out _));
    }

    [Fact]
    public void ShallowWaterIsWadedButDeepWaterHasNoFloor()
    {
      // A metre of water over the next tile's bed is the most the server lets an agent stand in.
      Assert.Equal(WalkRules.Step.Allowed, Check(Chunk(_ => 40, x => x < 6 ? 0 : 1), 5, 40, 6, out _));
      Assert.Equal(WalkRules.Step.Refused, Check(Chunk(_ => 40, x => x < 6 ? 0 : 2), 5, 40, 6, out _));
    }

    [Fact]
    public void LavaIsNoHeadroom()
    {
      Assert.Equal(WalkRules.Step.Refused, Check(Chunk(_ => 40, x => x < 6 ? 0 : 1, Lava), 5, 40, 6, out _));
    }

    [Fact]
    public void GroundThatIsNotStreamedIsTakenOnTrust()
    {
      // As the server lets a step through where it holds no walkability.
      Assert.Equal(WalkRules.Step.Unknown, Check(null, 5, 40, 6, out _));
    }
  }
}
