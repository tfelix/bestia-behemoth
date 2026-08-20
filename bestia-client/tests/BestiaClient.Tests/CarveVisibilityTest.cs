using System;
using System.Linq;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// How much rock has to be removed before the mesher draws anything.
  /// </summary>
  /// <remarks>
  /// The engine only ever *removes* terrain - mining, and spells that destroy landscape - so what a carve looks
  /// like is not a detail, it is the whole of what a player sees when they dig. And surface nets cannot draw an
  /// arbitrarily small one.
  ///
  /// <para>
  /// The reason is in the field: a corner sample is the mean of the eight cells meeting at it
  /// (<see cref="SurfaceNets"/>), so the field is a two-cell-wide box blur of occupancy and features thinner than
  /// two voxels are unrepresentable. A corner only falls below the isolevel once <b>more than half the material in
  /// some 2x2x2 voxel box is gone</b>, which is a volumetric threshold - sub-voxel precision does not lower it,
  /// because the blur only ever sees the total.
  /// </para>
  ///
  /// <para>
  /// <b>The collapse is asymmetric</b>, because <c>value &gt;= Iso</c> breaks the tie toward solid. A one-voxel
  /// *void* vanishes; a one-voxel *solid* renders correctly, at proper thickness, because the air cell beside it
  /// has one corner at 0.0 and one at 0.5 and that straddles. Reading it the other way round argues for deleting
  /// thin walls, which would delete geometry that was never broken -
  /// <see cref="AOneVoxelWallBetweenTwoGalleriesIsDrawn"/> is the guard against that.
  /// </para>
  ///
  /// <para>
  /// So the server enforces a minimum bore radius instead of trying to mesh a smaller one, and these tests are
  /// what stop that constant being tuned into the invisible range. Apparent bore is about <c>2R - 1</c>: the blur
  /// eats half a voxel of radius in every direction.
  /// </para>
  /// </remarks>
  public class CarveVisibilityTest
  {
    /// <summary>Must match <c>CarveBrush.MIN_RADIUS</c> on the server.</summary>
    private const double MinRadius = 2.0;

    /// <summary>Centre of the chunk, on a voxel centre, well away from every edge and apron.</summary>
    private const double CentreX = 16.5;
    private const double CentreY = 16.5;
    private const double CentreZ = 128.5;

    /// <summary>
    /// A carved chunk ringed by solid rock, so the apron is genuine rather than extended.
    /// </summary>
    private static FakeChunkSource Buried(VoxelChunk carved)
    {
      var source = new FakeChunkSource();

      for (var chunkY = -1; chunkY <= 1; chunkY++)
      {
        for (var chunkX = -1; chunkX <= 1; chunkX++)
        {
          source.Put(chunkX == 0 && chunkY == 0
            ? carved
            : TerrainFixtures.Uniform(chunkX, chunkY, 0, TerrainFixtures.Granite, 255));
        }
      }

      return source;
    }

    private static ChunkMesh Mesh(FakeChunkSource source) =>
      SurfaceNets.Build(source, new ChunkKey(0, 0, 0), TerrainFixtures.Appearance(), 1.0f, ChunkWrap.None);

    private static ChunkMesh MeshOfSphere(double radius) =>
      Mesh(Buried(TerrainFixtures.SolidWithSphere(0, 0, 0, CentreX, CentreY, CentreZ, radius)));

    /// <summary>
    /// Widest horizontal extent of the drawn surface, in metres - what the tunnel looks like to the player.
    /// </summary>
    /// <remarks>
    /// Godot X is the server's voxel x (<c>PositionComponent</c>'s swap puts server z on Godot Y), and the only
    /// geometry in these chunks is the carve, so the extent of every vertex *is* the bore.
    /// </remarks>
    private static double ApparentBore(ChunkMesh mesh)
    {
      var xs = mesh.Terrain.Vertices.Select(v => (double)v.X).ToList();

      return xs.Max() - xs.Min();
    }

    /// <summary>
    /// One voxel is not enough, and no amount of care in writing it makes it enough.
    /// </summary>
    /// <remarks>
    /// All eight corners of the carved cell come out at <c>(7*255 + 0) / (8*255) = 0.875</c> - each averages in
    /// exactly one empty cell - so every corner is inside, no sign change exists anywhere in the neighbourhood,
    /// and not one vertex is emitted. The player mines and sees nothing, while the server records air: the server
    /// then answers line of sight and walkability through rock the client is still drawing.
    /// </remarks>
    [Fact]
    public void ASingleCarvedVoxelDrawsNothing()
    {
      var chunk = TerrainFixtures.Uniform(0, 0, 0, TerrainFixtures.Granite, 255);
      var index = ((int)CentreY * TerrainFixtures.Size + (int)CentreX) * TerrainFixtures.Height + (int)CentreZ;

      chunk.Blocks[index] = TerrainFixtures.Air;
      chunk.Occupancy[index] = 0;

      Assert.True(Mesh(Buried(chunk)).IsEmpty);
    }

    /// <summary>
    /// Sub-voxel precision buys smoothness, not visibility.
    /// </summary>
    /// <remarks>
    /// A radius of 1.2 removes over seven cubic metres of rock, written to a fifth of a percent of a voxel, and
    /// still draws nothing at all - because no 2x2x2 box anywhere loses more than half its material. Anyone
    /// expecting fractional occupancy to also rescue a small tool will be wrong, and this is where they find out.
    /// </remarks>
    [Theory]
    [InlineData(1.0)]
    [InlineData(1.2)]
    public void ASphereBelowTheResolutionFloorDrawsNothing(double radius)
    {
      Assert.True(MeshOfSphere(radius).IsEmpty);
    }

    /// <summary>Above the floor a carve draws, and the bore grows with the radius as <c>2R - 1</c>.</summary>
    [Theory]
    [InlineData(1.3, 1.0)]
    [InlineData(1.4, 1.4)]
    [InlineData(1.6, 2.1)]
    [InlineData(2.0, 3.2)]
    [InlineData(3.0, 5.0)]
    public void ASphereAboveTheFloorDrawsAtRoughlyTwiceItsRadius(double radius, double atLeast)
    {
      var mesh = MeshOfSphere(radius);

      Assert.NotNull(mesh?.Terrain);
      Assert.True(
        ApparentBore(mesh) >= atLeast,
        $"a radius of {radius} bored {ApparentBore(mesh):F2} m, expected at least {atLeast:F2} m");
    }

    /// <summary>
    /// The server's minimum bore is comfortably clear of the floor, and reads at a walkable scale.
    /// </summary>
    /// <remarks>
    /// The one assertion that fails if <c>CarveBrush.MIN_RADIUS</c> is ever lowered towards the cliff at 1.3.
    /// Three metres is about what a player can walk down without the tunnel reading as a crawlspace.
    /// </remarks>
    [Fact]
    public void TheMinimumBoreRadiusRendersAsAWalkableGallery()
    {
      var mesh = MeshOfSphere(MinRadius);

      Assert.NotNull(mesh?.Terrain);
      Assert.True(
        ApparentBore(mesh) >= 3.0,
        $"the minimum bore radius {MinRadius} rendered only {ApparentBore(mesh):F2} m across");
    }

    /// <summary>
    /// A wall one voxel thick between two galleries is drawn, at both faces, one metre apart.
    /// </summary>
    /// <remarks>
    /// The regression guard for the asymmetry. A thin solid is *not* the dual of a thin void: the cells either side
    /// of the wall each have one corner at 0.0 and one at 0.5, so both faces cross cleanly and land exactly on the
    /// wall's own boundaries. If this ever starts failing, the fix is not to remove thin walls server-side - it is
    /// to find out what changed in the field construction.
    /// </remarks>
    [Fact]
    public void AOneVoxelWallBetweenTwoGalleriesIsDrawn()
    {
      const int WallX = 16;
      const int ZLo = 120;
      const int ZHi = 136;

      var mesh = Mesh(Buried(
        TerrainFixtures.SolidWithWalledGalleries(0, 0, 0, WallX, 5, ZLo, ZHi)));

      Assert.NotNull(mesh?.Terrain);

      // Vertices in the middle of the galleries vertically, so the tunnel floor and roof cannot be mistaken for
      // the wall. Godot Y carries the server's voxel z.
      var faces = mesh.Terrain.Vertices
        .Where(v => v.Y > ZLo + 2 && v.Y < ZHi - 2)
        .Select(v => Math.Round(v.X, 3))
        .Distinct()
        .OrderBy(x => x)
        .ToList();

      Assert.Contains((double)WallX, faces);
      Assert.Contains((double)WallX + 1.0, faces);
    }
  }
}
