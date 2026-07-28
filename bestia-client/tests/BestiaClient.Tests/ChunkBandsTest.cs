using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The band scan, which decides how much work meshing a chunk is allowed to be.
  /// </summary>
  public class ChunkBandsTest
  {
    [Fact]
    public void SolidRockHasNoInteriorBoundary()
    {
      var bands = ChunkBands.Of(TerrainFixtures.Uniform(0, 0, 0, TerrainFixtures.Granite, 255));

      Assert.True(bands.IsUniform);
      Assert.Equal(-1, bands.MaxActiveZ);
    }

    [Fact]
    public void OpenAirHasNoInteriorBoundary()
    {
      Assert.True(ChunkBands.Of(TerrainFixtures.Uniform(0, 0, 0, TerrainFixtures.Air, 0)).IsUniform);
    }

    /// <summary>
    /// Flat terrain marks a handful of cells around its air interface and nothing else.
    /// </summary>
    /// <remarks>
    /// The measurement behind the whole approach: 262 144 cells in a chunk, and a surface touches four of them per
    /// column. If this band ever widened to the full height, meshing would be sixty times the work for the same
    /// picture.
    /// </remarks>
    [Fact]
    public void FlatTerrainMarksOnlyTheAirInterface()
    {
      var bands = ChunkBands.Of(TerrainFixtures.Flat(0, 0, 0, 40.3));

      Assert.False(bands.IsUniform);

      // Two boundaries - 255 to 76 at cell 40, and 76 to 0 at cell 41 - each reaching the cell below it and
      // itself, so cells 39, 40 and 41. Three of a column's 256, and 41 turns out to have no crossing in it: the
      // mask is a superset of what actually emits, which is the safe direction for it to be wrong in.
      Assert.Equal(39, bands.MinActiveZ);
      Assert.Equal(41, bands.MaxActiveZ);
    }

    /// <summary>
    /// A cave leaves two thin bands with untouched rock between them, and the mask keeps them apart.
    /// </summary>
    /// <remarks>
    /// The reason this is a bitmask and not a min/max range. The range here spans 22 m of solid granite that has no
    /// surface in it, and a range-only structure would mesh all of it.
    /// </remarks>
    [Fact]
    public void CaveLeavesAGapBetweenItsBands()
    {
      var bands = ChunkBands.Of(TerrainFixtures.WithCave(0, 0, 60.0, floor: 20, roof: 30));

      var column = bands.ColumnMask(0, 0);

      Assert.True(Marked(column, 20), "the cave floor should be marked");
      Assert.True(Marked(column, 30), "the cave roof should be marked");
      Assert.False(Marked(column, 25), "open air inside the cave carries no surface");
      Assert.False(Marked(column, 45), "solid rock above the cave carries no surface");
    }

    /// <summary>
    /// Rock beside air with no vertical boundary anywhere is a cliff, and must not be called uniform.
    /// </summary>
    /// <remarks>
    /// Not producible by the generator - it would be a 256 m sheer face inside one chunk - but the uniform check is
    /// the one that skips a chunk entirely, so it has to be wrong in the safe direction. Player excavation is the
    /// plausible route to it.
    /// </remarks>
    [Fact]
    public void ColumnsThatDisagreeAreNotUniform()
    {
      var chunk = TerrainFixtures.Uniform(0, 0, 0, TerrainFixtures.Granite, 255);

      // Empty the far half of the chunk from top to bottom, leaving no run boundary in any single column.
      for (var localY = 0; localY < TerrainFixtures.Size; localY++)
      {
        for (var localX = TerrainFixtures.Size / 2; localX < TerrainFixtures.Size; localX++)
        {
          var offset = (localY * TerrainFixtures.Size + localX) * TerrainFixtures.Height;

          for (var z = 0; z < TerrainFixtures.Height; z++)
          {
            chunk.Blocks[offset + z] = TerrainFixtures.Air;
            chunk.Occupancy[offset + z] = 0;
          }
        }
      }

      var bands = ChunkBands.Of(chunk);

      Assert.False(bands.IsUniform);
      Assert.Equal(0, bands.MinActiveZ);
      Assert.Equal(TerrainFixtures.Height - 1, bands.MaxActiveZ);
    }

    /// <summary>A seam exists only where the two chunks actually disagree about their shared face.</summary>
    [Fact]
    public void FloorSeamIsDetectedOnlyWhenTheNeighbourDiffers()
    {
      var air = TerrainFixtures.Uniform(0, 0, 0, TerrainFixtures.Air, 0);
      var rock = TerrainFixtures.Uniform(0, 0, -1, TerrainFixtures.Granite, 255);

      Assert.True(ChunkBands.SeamAtFloor(air, rock));
      Assert.False(ChunkBands.SeamAtFloor(air, TerrainFixtures.Uniform(0, 0, -1, TerrainFixtures.Air, 0)));

      // A missing neighbour is not a seam. Reading absent terrain as air would paint a floor across the bottom of
      // every chunk at the edge of what has been streamed.
      Assert.False(ChunkBands.SeamAtFloor(air, null));
    }

    private static bool Marked(System.ReadOnlySpan<ulong> column, int cell) =>
      (column[cell >> 6] & (1UL << (cell & 63))) != 0;
  }
}
