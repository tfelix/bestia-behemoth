using System;
using System.IO;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// Decoding a chunk patch and applying it to a held chunk: the client half of the server's
  /// <c>ChunkEditCodec</c>.
  /// </summary>
  public class ChunkEditCodecTest
  {
    // Ids as the server's BlockType has them.
    private const byte Water = 1;
    private const byte Granite = 4;

    private static VoxelChunk Filled(byte block, byte occupancy)
    {
      const int size = 4;
      const int height = 8;
      var blocks = new byte[size * size * height];
      var occupancies = new byte[blocks.Length];
      Array.Fill(blocks, block);
      Array.Fill(occupancies, occupancy);

      return new VoxelChunk(0, 0, 0, size, height, blocks, occupancies);
    }

    [Fact]
    public void GapsAddUpToAbsoluteIndices()
    {
      // Index 300 is the varint 0xAC 0x02, then two edits one voxel apart.
      var bytes = new byte[] { 0xAC, 0x02, Water, 200, 1, Water, 255, 1, 0, 0 };

      var edits = ChunkEditCodec.Decode(bytes);

      Assert.Equal(3, edits.Count);
      Assert.Equal(300, edits[0].Index);
      Assert.Equal(Water, edits[0].BlockId);
      Assert.Equal(200, edits[0].Occupancy);
      Assert.Equal(301, edits[1].Index);
      Assert.Equal(302, edits[2].Index);
      Assert.Equal(VoxelChunk.AirBlockId, edits[2].BlockId);
    }

    [Fact]
    public void HalfAnEditIsRefused()
    {
      Assert.Throws<InvalidDataException>(() => ChunkEditCodec.Decode(new byte[] { 5, Water }));
    }

    [Fact]
    public void ATruncatedIndexIsRefused()
    {
      Assert.Throws<InvalidDataException>(() => ChunkEditCodec.Decode(new byte[] { 0x80 }));
    }

    [Fact]
    public void WaterCanFillAir()
    {
      var chunk = Filled(VoxelChunk.AirBlockId, 0);

      chunk.ApplyEdit(10, Water, 90);

      Assert.Equal(Water, chunk.Blocks[10]);
      Assert.Equal(90, chunk.Occupancy[10]);
      chunk.Validate();
    }

    [Fact]
    public void AnEditCanEmptyAVoxel()
    {
      var chunk = Filled(Granite, 255);

      chunk.ApplyEdit(10, VoxelChunk.AirBlockId, 0);

      Assert.Equal(VoxelChunk.AirBlockId, chunk.Blocks[10]);
      chunk.Validate();
    }

    [Fact]
    public void AirWithMaterialIsRefused()
    {
      var chunk = Filled(VoxelChunk.AirBlockId, 0);

      Assert.Throws<ArgumentException>(() => chunk.ApplyEdit(10, VoxelChunk.AirBlockId, 5));
      Assert.Throws<ArgumentException>(() => chunk.ApplyEdit(10, Water, 0));
    }

    [Fact]
    public void AnIndexOutsideTheChunkIsRefused()
    {
      var chunk = Filled(VoxelChunk.AirBlockId, 0);

      Assert.Throws<ArgumentOutOfRangeException>(() => chunk.ApplyEdit(chunk.Volume, Water, 1));
    }
  }
}
