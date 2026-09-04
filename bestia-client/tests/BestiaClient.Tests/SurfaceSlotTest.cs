using System;
using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// What the mesher says each vertex is made of: the slot weights, and the tint over them.
  /// </summary>
  /// <remarks>
  /// Separate from <see cref="SurfaceNetsTest"/>, which is about where geometry lands. Nothing here would notice
  /// if every vertex were in the wrong place, and nothing there would notice if every vertex were the wrong
  /// material - so a failure in one file says which half of the mesher to open.
  /// </remarks>
  public class SurfaceSlotTest
  {
    private const int Size = TerrainFixtures.Size;

    private static readonly BlockAppearance Appearance = TerrainFixtures.Appearance();

    private static FakeChunkSource Surrounded(Func<int, int, VoxelChunk> build, int radius = 2)
    {
      var source = new FakeChunkSource();

      for (var chunkY = -radius; chunkY <= radius; chunkY++)
      {
        for (var chunkX = -radius; chunkX <= radius; chunkX++)
        {
          source.Put(build(chunkX, chunkY));
        }
      }

      return source;
    }

    private static ChunkMesh Mesh(FakeChunkSource source, int chunkX = 0, int chunkY = 0) =>
      SurfaceNets.Build(source, new ChunkKey(chunkX, chunkY, 0), Appearance, 1.0f, ChunkWrap.None);

    /// <summary>Every weight of one vertex, reassembled from the four channels they are split across.</summary>
    private static int[] WeightsAt(ChunkSurface surface, int vertex)
    {
      var weights = new int[BlockAppearance.Slots];
      var channels = Channels(surface);

      for (var channel = 0; channel < channels.Length; channel++)
      {
        for (var lane = 0; lane < 4; lane++)
        {
          weights[channel * 4 + lane] = channels[channel][vertex * 4 + lane];
        }
      }

      return weights;
    }

    /// <summary>The custom vertex channels the weights are split across, in slot order.</summary>
    private static byte[][] Channels(ChunkSurface surface) => new[]
    {
      surface.SlotWeights0, surface.SlotWeights1, surface.SlotWeights2, surface.SlotWeights3
    };

    private static int WeightOf(ChunkSurface surface, int vertex, BlockAppearance.SurfaceSlot slot) =>
      WeightsAt(surface, vertex)[(int)slot];

    /// <summary>Vertices away from the chunk's own edges, where the neighbourhood is genuine.</summary>
    private static IEnumerable<int> Interior(ChunkSurface surface) =>
      Enumerable.Range(0, surface.Vertices.Length)
        .Where(i =>
          surface.Vertices[i].X > 2 && surface.Vertices[i].X < Size - 2 &&
          surface.Vertices[i].Z > 2 && surface.Vertices[i].Z < Size - 2);

    /// <summary>
    /// The length contract <c>ArrayMesh</c> enforces, checked here because there it fails silently.
    /// </summary>
    /// <remarks>
    /// An eight-bit custom channel must be exactly four bytes per vertex. Godot does not round, pad or complain
    /// its way past a mismatch - it refuses the arrays and adds no surface at all, so a chunk with a
    /// one-vertex discrepancy renders as nothing and looks exactly like a chunk that never arrived. Every surface
    /// is checked and not just terrain, because all three go through the same call.
    /// </remarks>
    [Fact]
    public void EverySurfaceCarriesFourWeightBytesPerVertex()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Rolling(x, y, 40, 44));
      var mesh = Mesh(source);

      var checkedAny = false;

      foreach (var surface in mesh.Surfaces.Where(s => s != null))
      {
        // Every channel, not just the first two. A channel left unfilled uploads as whatever the attribute
        // defaults to, and the slots in it would then read as present-but-zero rather than as absent.
        foreach (var channel in Channels(surface))
        {
          Assert.Equal(surface.Vertices.Length * 4, channel.Length);
        }

        checkedAny = true;
      }

      // The fixture has water in its hollows, so this is asserting the loop above ran on more than terrain.
      Assert.True(checkedAny);
      Assert.NotNull(mesh.Water);
    }

    /// <summary>
    /// Weights are a partition, everywhere, on every surface.
    /// </summary>
    /// <remarks>
    /// Exactly 255 rather than close to it, because the shader is entitled to skip a normalising divide per pixel
    /// on the strength of it. Driven over hilly ground with standing water rather than a flat plane, so the vertices
    /// that only exist on relief - cliff faces, waterlines, the cells where three materials meet - are included.
    /// </remarks>
    [Fact]
    public void WeightsSumToExactly255AtEveryVertex()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Rolling(x, y, 40, 44));
      var mesh = Mesh(source);

      foreach (var surface in mesh.Surfaces.Where(s => s != null))
      {
        for (var vertex = 0; vertex < surface.Vertices.Length; vertex++)
        {
          Assert.Equal(255, WeightsAt(surface, vertex).Sum());
        }
      }
    }

    /// <summary>
    /// One material everywhere means one slot at full weight, with nothing bleeding into the others.
    /// </summary>
    /// <remarks>
    /// The floor under everything else here. If a uniform surface cannot come back as a single slot then no
    /// assertion about a boundary means anything, because the boundary's blend would be indistinguishable from
    /// the same leak happening twice.
    /// </remarks>
    [Fact]
    public void UniformCoverIsEntirelyItsOwnSlot()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Capped(x, y, 40.3, (_, _) => TerrainFixtures.Grass));
      var terrain = Mesh(source).Terrain;

      Assert.NotNull(terrain);

      var interior = Interior(terrain).ToList();
      Assert.NotEmpty(interior);

      foreach (var vertex in interior)
      {
        var weights = WeightsAt(terrain, vertex);

        Assert.Equal(255, weights[(int)BlockAppearance.SurfaceSlot.Grass]);

        for (var slot = 0; slot < BlockAppearance.Slots; slot++)
        {
          if (slot != (int)BlockAppearance.SurfaceSlot.Grass)
          {
            Assert.Equal(0, weights[slot]);
          }
        }
      }
    }

    /// <summary>
    /// A surface cap one voxel thick outranks the soil column it lies on.
    /// </summary>
    /// <remarks>
    /// The regression test for grassy ground rendering as dirt, which it did for as long as the mesher scored a
    /// cell by how much material was in it. The generator writes one cap voxel over full soil and makes that cap
    /// the only partial voxel in the column, so the buried soil scored 255 against the grass's 77 and won both
    /// the tint and two fifths of the splat.
    ///
    /// <para>
    /// Driven off <see cref="TerrainFixtures.Soiled"/> rather than <see cref="TerrainFixtures.Capped"/>, and that
    /// is the whole reason the fixture exists: <c>Capped</c> lays its cover three voxels deep, which put a second
    /// cap voxel under the partial one and hid this for the life of the file.
    /// </para>
    /// </remarks>
    [Fact]
    public void TheSurfaceCapOutranksTheSoilItLiesOn()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Soiled(
        x, y, (_, _) => 40.3, (_, _) => TerrainFixtures.Grass));

      var terrain = Mesh(source).Terrain;
      Assert.NotNull(terrain);

      var grass = Appearance.ColourOf(TerrainFixtures.Grass);
      var interior = Interior(terrain).ToList();

      Assert.NotEmpty(interior);

      foreach (var vertex in interior)
      {
        Assert.Equal(grass, terrain.Colours[vertex]);
        Assert.Equal(0, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Soil));
        Assert.Equal(255, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Grass));
      }
    }

    /// <summary>
    /// It outranks it at any thickness, down to the thinnest cap a byte can carry.
    /// </summary>
    /// <remarks>
    /// A surface two centimetres above a lattice plane quantises to an occupancy of five, so the cap is beaten
    /// fifty to one on volume and still has to win. Worth its own test because it separates a burial rule from a
    /// threshold: anything that merely weighted the cap more heavily would pass the flat case above and fail here,
    /// and the elevation that produces it is no rarer than any other.
    /// </remarks>
    [Fact]
    public void AThinCapOutranksItStill()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Soiled(
        x, y, (_, _) => 40.02, (_, _) => TerrainFixtures.Grass));

      var terrain = Mesh(source).Terrain;
      Assert.NotNull(terrain);

      var interior = Interior(terrain).ToList();
      Assert.NotEmpty(interior);

      foreach (var vertex in interior)
      {
        Assert.Equal(0, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Soil));
        Assert.Equal(255, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Grass));
      }
    }

    /// <summary>
    /// Ground that slopes is still made of what covers it, at every vertex on it.
    /// </summary>
    /// <remarks>
    /// Two things go wrong on a slope that do not go wrong on a plain, and both of them put soil on the surface.
    /// A one-voxel step sets the neighbouring column's full soil <i>beside</i> the cap rather than under it. And
    /// at every lattice crossing the mesher emits a second vertex whose own cell <i>is</i> that full soil - about
    /// one vertex in five at this gradient - which is why the rule cannot be "the cell holding the vertex wins".
    ///
    /// <para>
    /// Asserted over every vertex rather than the flat-normalled ones, because nothing in this fixture exposes
    /// soil anywhere: the cap covers every column completely, so any soil in the splat at all is soil showing
    /// through a metre of turf.
    /// </para>
    /// </remarks>
    [Fact]
    public void SlopedGroundIsMadeOfItsCapAndNotTheSoilUnderIt()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Soiled(
        x, y, (worldX, _) => 40.3 + worldX * 0.25, (_, _) => TerrainFixtures.Grass));

      var terrain = Mesh(source).Terrain;
      Assert.NotNull(terrain);

      var grass = Appearance.ColourOf(TerrainFixtures.Grass);
      var interior = Interior(terrain).ToList();

      // A metre of relief every four columns, so the extra vertices this is about are certainly in here.
      Assert.InRange(interior.Count, Size * Size / 2, Size * Size * 2);

      foreach (var vertex in interior)
      {
        Assert.Equal(0, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Soil));
        Assert.Equal(grass, terrain.Colours[vertex]);
      }
    }

    /// <summary>
    /// A lake bed takes its cap through the water standing on it.
    /// </summary>
    /// <remarks>
    /// The guard on the burial rule, and it has to pass before the rule exists as well as after. Submerged, the
    /// fluid is the topmost written voxel and so the partial one, which leaves the cap full and puts the vertex up
    /// in a water cell that the terrain pass masks to nothing. The material can then only come from below - the
    /// exact direction the rule refuses on dry land - and it is only allowed to because the water overhead is not
    /// this surface's material and so does not bury anything.
    ///
    /// <para>
    /// The Neutral assertion is the interesting half. If burial ever swallowed the last scoring cell,
    /// <c>PackWeights</c> would paint the bed grey rather than fail, and grey on a lake bed is not a look anyone
    /// would file as a bug.
    /// </para>
    /// </remarks>
    [Fact]
    public void SubmergedGroundTakesItsCapThroughTheWaterAboveIt()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Soiled(
        x, y, (_, _) => 40.3, (_, _) => TerrainFixtures.Sand, waterLevel: 44));

      var mesh = Mesh(source);
      var terrain = mesh.Terrain;

      Assert.NotNull(terrain);
      Assert.NotNull(mesh.Water);

      var sand = Appearance.ColourOf(TerrainFixtures.Sand);
      var interior = Interior(terrain).ToList();

      Assert.NotEmpty(interior);

      foreach (var vertex in interior)
      {
        Assert.Equal(sand, terrain.Colours[vertex]);
        Assert.Equal(255, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Sand));
        Assert.Equal(0, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Neutral));
      }
    }

    /// <summary>
    /// A wall cut through two beds keeps both, and mixes them only at the contact.
    /// </summary>
    /// <remarks>
    /// The other side of the burial rule, on the one shape that puts a vertex inside a completely full cell: a
    /// staircase. <see cref="TerrainFixtures.DiagonalWall"/> has no partial cell anywhere, so nothing here is
    /// about surface caps - the question is whether burial, having been given a reason to skip cells, goes on to
    /// skip the ones a wall is actually made of.
    ///
    /// <para>
    /// It does not, and the shape of the answer is worth stating because it is not the obvious one. The vertices
    /// <i>inside</i> the wall reach only their own bed: the cell beneath such a vertex is walled in on all six
    /// faces, so it is buried in the ordinary sense and drops out, and the rock that is genuinely exposed further
    /// down the face lies diagonally from them and was never in a six-neighbour stencil to begin with. The blend
    /// comes instead from the vertices sitting in the air cells just off the face, which touch one exposed cell
    /// of each bed and split evenly between them. So the contact is one row of vertices wide, from the side of it
    /// that can see both - which is a better answer than the one this test was first written to expect.
    /// </para>
    /// </remarks>
    [Fact]
    public void AWallOfTwoBedsBlendsThemOnlyWhereTheyMeet()
    {
      const int BedTop = 40;
      const int Intercept = 57;

      var source = Surrounded((x, y) => TerrainFixtures.DiagonalWall(x, y, Intercept, BedTop));

      var terrain = Mesh(source).Terrain;
      Assert.NotNull(terrain);

      var face = Interior(terrain).ToList();
      Assert.NotEmpty(face);

      var pureSand = 0;
      var pureRock = 0;
      var mixed = 0;

      foreach (var vertex in face)
      {
        var weights = WeightsAt(terrain, vertex);

        var sand = weights[(int)BlockAppearance.SurfaceSlot.Sand];
        var rock = weights[(int)BlockAppearance.SurfaceSlot.Rock];

        // Nothing else is in reach. A third slot would mean the scan had wandered off the face.
        Assert.Equal(255, sand + rock);

        if (sand == 255)
        {
          pureSand++;
        }
        else if (rock == 255)
        {
          pureRock++;
        }
        else
        {
          mixed++;
        }
      }

      // Both beds are on the face, or this would pass on a wall made of one rock.
      Assert.True(pureSand > 0);
      Assert.True(pureRock > 0);

      // One row of mixed vertices along the contact and no more. The blend comes from the vertices sitting in the
      // air cells just off the face, which touch one exposed cell of each bed; the vertices *inside* the wall
      // reach only their own bed, because the cell under them is walled in on all six sides and no longer counts.
      // A much larger count would mean burial had stopped applying and the beds were smearing into each other.
      Assert.InRange(mixed, Size / 2, Size * 2);
    }

    /// <summary>
    /// A gallery wall names its own rock rather than falling through to grey.
    /// </summary>
    /// <remarks>
    /// The fallback, which is the one branch of the burial rule that no other test reaches. Beside a vertical
    /// face the vertex sits in air, the only cell holding anything is the wall next to it, and that wall has more
    /// wall directly on top of it - so every candidate is buried and skipping them all would leave nothing to
    /// name. <see cref="PackWeights"/> answers grey when it is handed nothing, so the failure this guards against
    /// is not an exception but a mine shaft rendered in featureless neutral.
    /// </remarks>
    [Fact]
    public void AGalleryWallNamesItsOwnRock()
    {
      var source = Surrounded((x, y) =>
        TerrainFixtures.SolidWithWalledGalleries(x, y, 0, wallX: 16, span: 6, zLo: 30, zHi: 40));

      var terrain = Mesh(source).Terrain;
      Assert.NotNull(terrain);

      var granite = Appearance.ColourOf(TerrainFixtures.Granite);
      var interior = Interior(terrain).ToList();

      Assert.NotEmpty(interior);

      foreach (var vertex in interior)
      {
        Assert.Equal(granite, terrain.Colours[vertex]);
        Assert.Equal(255, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Rock));
        Assert.Equal(0, WeightOf(terrain, vertex, BlockAppearance.SurfaceSlot.Neutral));
      }
    }

    /// <summary>
    /// Two chunks agree about a seam vertex over the column the generator actually writes.
    /// </summary>
    /// <remarks>
    /// <see cref="SeamVerticesAgreeBetweenAdjacentChunks"/> drives the same guarantee over
    /// <see cref="TerrainFixtures.Capped"/>, where every cell in reach of a seam vertex is full or is the cap. The
    /// burial rule reads a cell that the old scan did not - the roof over each lateral - and on a real column
    /// those roofs are the partial cap voxels, so this is the fixture that can tell whether the extra read is
    /// gathered identically on both sides of a boundary or only nearly so.
    /// </remarks>
    [Fact]
    public void SeamVerticesAgreeOverTheGeneratorsOwnColumn()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Soiled(
        x, y,
        (worldX, _) => 40.3 + worldX * 0.25,
        (worldX, _) => worldX < Size ? TerrainFixtures.Grass : TerrainFixtures.Sand));

      var left = Mesh(source).Terrain;
      var right = Mesh(source, chunkX: 1).Terrain;

      Assert.NotNull(left);
      Assert.NotNull(right);

      var byPosition = new Dictionary<(long, long, long), int>();

      for (var i = 0; i < left.Vertices.Length; i++)
      {
        byPosition[SeamKey(left.Vertices[i])] = i;
      }

      var shared = 0;

      for (var i = 0; i < right.Vertices.Length; i++)
      {
        if (!byPosition.TryGetValue(SeamKey(right.Vertices[i]), out var mirror))
        {
          continue;
        }

        Assert.Equal(left.Colours[mirror], right.Colours[i]);
        Assert.Equal(WeightsAt(left, mirror), WeightsAt(right, i));

        shared++;
      }

      // A sloped fixture puts about two vertices in each column, so the seam carries more than one per row.
      Assert.InRange(shared, Size - 2, Size * 3);
    }

    /// <summary>
    /// Where two materials meet, both are present and nothing else is.
    /// </summary>
    /// <remarks>
    /// The blend is the entire reason weights exist rather than a single slot index per vertex, so it is worth
    /// asserting from both sides: that the boundary genuinely mixes, and that the mixing is confined to it. A
    /// third slot appearing anywhere would mean the neighbourhood scan is reaching material it should not - and
    /// the granite under the cover is exactly the material it would reach.
    /// </remarks>
    [Fact]
    public void AMaterialBoundaryBlendsThoseTwoSlotsAndNoOthers()
    {
      const int Boundary = 16;

      var source = Surrounded((x, y) => TerrainFixtures.Capped(
        x, y, 40.3, (worldX, _) => worldX < Boundary ? TerrainFixtures.Grass : TerrainFixtures.Sand));

      var terrain = Mesh(source).Terrain;
      Assert.NotNull(terrain);

      var mixed = 0;

      foreach (var vertex in Interior(terrain))
      {
        var weights = WeightsAt(terrain, vertex);

        var grass = weights[(int)BlockAppearance.SurfaceSlot.Grass];
        var sand = weights[(int)BlockAppearance.SurfaceSlot.Sand];

        Assert.Equal(255, grass + sand);

        if (grass > 0 && sand > 0)
        {
          mixed++;
        }
      }

      // One straddling column of vertices down the length of the chunk, give or take the ends the interior
      // filter trims. Asserting a plausible count rather than "more than zero" because a mesher that blended
      // everything with everything would also pass that.
      Assert.InRange(mixed, Size / 2, Size * 3);
    }

    /// <summary>
    /// The tint is the fullest single material, not an average of the neighbourhood.
    /// </summary>
    /// <remarks>
    /// This is the regression test for the one thing that would have been lost by accumulating the tint the way
    /// the weights accumulate. <c>BlockAppearance</c>'s ore rows exist so a player can tell the rim of a body from
    /// its middle; a single ore voxel surrounded by granite is the case that decides whether they can, and under
    /// an averaged tint it would come back as a sixth of a gold stain on both the ore and its neighbours.
    ///
    /// <para>
    /// Both halves matter. That the ore vertex is gold says the ore is visible; that the vertex beside it is
    /// granite says the ore has an edge, which is the part a player reads as "the seam stops here".
    /// </para>
    /// </remarks>
    [Fact]
    public void AnOreVoxelTintsItsOwnVertexAndNotItsNeighbour()
    {
      const int OreX = 16;
      const int OreY = 16;

      var source = Surrounded((x, y) => TerrainFixtures.Capped(
        x, y, 40.3,
        (worldX, worldY) => worldX == OreX && worldY == OreY ? TerrainFixtures.GoldOre : TerrainFixtures.Granite));

      var terrain = Mesh(source).Terrain;
      Assert.NotNull(terrain);

      var gold = Appearance.ColourOf(TerrainFixtures.GoldOre);
      var granite = Appearance.ColourOf(TerrainFixtures.Granite);

      var atOre = VertexOver(terrain, OreX, OreY);
      var beside = VertexOver(terrain, OreX + 2, OreY);

      Assert.Equal(gold, terrain.Colours[atOre]);
      Assert.Equal(granite, terrain.Colours[beside]);

      // Ore is host rock with metal in it, so both vertices are wholly the rock slot and only the tint differs.
      // That is the claim the slot table makes about the thirty ore rows, and it is cheap to check here.
      Assert.Equal(255, WeightOf(terrain, atOre, BlockAppearance.SurfaceSlot.Rock));
      Assert.Equal(255, WeightOf(terrain, beside, BlockAppearance.SurfaceSlot.Rock));
    }

    /// <summary>The vertex sitting over one voxel column, which on this fixture is exactly one.</summary>
    private static int VertexOver(ChunkSurface surface, int voxelX, int voxelY)
    {
      var found = Enumerable.Range(0, surface.Vertices.Length)
        .Where(i =>
          Math.Abs(surface.Vertices[i].X - (voxelX + 0.5f)) < 0.25f &&
          Math.Abs(surface.Vertices[i].Z - (voxelY + 0.5f)) < 0.25f)
        .ToList();

      Assert.Single(found);

      return found[0];
    }

    /// <summary>
    /// Two chunks meshing the same seam vertex must agree about it to the bit.
    /// </summary>
    /// <remarks>
    /// A chunk emits vertices for the cell just outside its own low edge, so the cell on a boundary is meshed
    /// twice - once by each neighbour - and the two copies are drawn touching. Position has always had to match or
    /// the surface would tear, but nothing asserted that the *attributes* did, and they are what a hairline of the
    /// wrong material at every chunk boundary would come from. The guarantee is that both patches gather the same
    /// seven cells and the accumulation over them is integer, so this is the test that the arithmetic stays that
    /// way: a well-meant float in <c>AccumulateSurface</c> would still be deterministic, but only until someone
    /// reordered the sum.
    ///
    /// <para>
    /// Driven across a material boundary placed on the seam itself, because two chunks agreeing on a vertex made
    /// of one material proves very little.
    /// </para>
    /// </remarks>
    [Fact]
    public void SeamVerticesAgreeBetweenAdjacentChunks()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Capped(
        x, y, 40.3, (worldX, _) => worldX < Size ? TerrainFixtures.Grass : TerrainFixtures.Sand));

      var left = Mesh(source).Terrain;
      var right = Mesh(source, chunkX: 1).Terrain;

      Assert.NotNull(left);
      Assert.NotNull(right);

      var byPosition = new Dictionary<(long, long, long), int>();

      for (var i = 0; i < left.Vertices.Length; i++)
      {
        byPosition[SeamKey(left.Vertices[i])] = i;
      }

      var shared = 0;

      for (var i = 0; i < right.Vertices.Length; i++)
      {
        if (!byPosition.TryGetValue(SeamKey(right.Vertices[i]), out var mirror))
        {
          continue;
        }

        Assert.Equal(left.Normals[mirror], right.Normals[i]);
        Assert.Equal(left.Colours[mirror], right.Colours[i]);
        Assert.Equal(WeightsAt(left, mirror), WeightsAt(right, i));

        shared++;
      }

      // One column of shared vertices down the seam. Anything much less means the match failed rather than the
      // agreement held, which would let this pass while testing nothing.
      Assert.InRange(shared, Size - 2, Size + 2);
    }

    /// <summary>A vertex position as an exact key, so the two chunks' copies of a seam vertex can be paired.</summary>
    private static (long, long, long) SeamKey(Vector3 v) => (
      (long)Math.Round(v.X * 4096.0),
      (long)Math.Round(v.Y * 4096.0),
      (long)Math.Round(v.Z * 4096.0));
  }
}
