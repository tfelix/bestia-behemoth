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
  /// The mesher's contract with the wire format.
  /// </summary>
  /// <remarks>
  /// Two of these caught real bugs during development, which is why they are here rather than in a scratch file:
  /// terrain sitting exactly on a chunk floor rendered as nothing, and the winding was only ever going to be
  /// verified by reasoning until something measured it.
  /// </remarks>
  public class SurfaceNetsTest
  {
    private const int Size = TerrainFixtures.Size;

    /// <summary>
    /// Surrounds a chunk with copies of itself so nothing at its edges is guesswork.
    /// </summary>
    /// <remarks>
    /// A missing neighbour is extended flat by design, so a chunk meshed alone has a synthetic ring around it. Its
    /// interior is still exact, which is what the interior-only assertions below rely on.
    /// </remarks>
    private static FakeChunkSource Surrounded(Func<int, int, VoxelChunk> build, int radius = 1)
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

    private static ChunkMesh Mesh(FakeChunkSource source, int chunkX = 0, int chunkY = 0, int chunkZ = 0) =>
      SurfaceNets.Build(source, new ChunkKey(chunkX, chunkY, chunkZ), TerrainFixtures.Appearance(), 1.0f, ChunkWrap.None);

    /// <summary>Vertices away from the chunk's own edges, where the neighbourhood is genuine.</summary>
    /// <remarks>
    /// <b>Worth knowing what this hides.</b> Its justification - a missing neighbour is extended flat - applies
    /// to a chunk meshed alone, and every caller below builds a <see cref="Surrounded"/> fixture where the ring
    /// is held and the apron falls inside a genuine neighbour. So on those it excludes a two-cell band that is
    /// exact, and that band is where seam bugs live: the submerged bank that was never meshed, and the water
    /// wall standing in front of it, were both invisible to every assertion in this file for exactly that
    /// reason. Prefer asserting over the whole vertex set on a surrounded fixture, and reach for this only
    /// where a chunk is genuinely meshed without neighbours.
    /// </remarks>
    private static List<Vector3> Interior(ChunkSurface surface) =>
      surface.Vertices.Where(v => v.X > 2 && v.X < Size - 2 && v.Z > 2 && v.Z < Size - 2).ToList();


    /// <summary>
    /// A river bank is meshed, and the water draws no wall against it.
    /// </summary>
    /// <remarks>
    /// <b>The case no fixture in this file used to produce, and the one the client rendered worst.</b> The
    /// shore fixture is normally driven with a gentle ramp, where the bed boundary sits within a cell or two
    /// of the waterline everywhere - so the dilated bed subtraction blanketed the whole shallow band and no
    /// wall survived to be noticed. A river is the opposite shape: a steep bank with several voxels of water
    /// against it, whose middle no vertical run boundary reaches.
    ///
    /// <para>
    /// <c>ChunkBands</c> records material boundaries by walking columns vertically, so it finds the bed and
    /// the waterline and nothing between; its horizontal pass compares occupancy, which is 255 on both sides
    /// of a submerged bank. The cells between entered neither mask and <b>the terrain pass never drew the
    /// bank at all</b> - measured on this fixture, zero vertices in the whole submerged band.
    /// </para>
    ///
    /// <para>
    /// Worth recording what this does <i>not</i> show, because it was the original theory. The water's own
    /// side wall is not emitted here either way: the fluid pass never visits those cells, for the same
    /// reason the terrain pass did not. The translucent slabs seen in game came from water standing
    /// genuinely above the surrounding ground - a server-side defect in how the bed was derived - and once
    /// the water sits in its channel the lateral faces are against rock, where occupancy does not change.
    /// The wall assertions below are kept as a guard on a property that should stay true, not as evidence:
    /// they pass with the fix reverted, and only the bank count below discriminates.
    /// </para>
    ///
    /// <para>
    /// Asserted over the full vertex set rather than through <see cref="Interior"/>, deliberately: the
    /// bank here is placed on the chunk seam, which is exactly where a fix applied inside <c>ChunkBands</c>
    /// would leave a one-cell gap every 32 m.
    /// </para>
    /// </remarks>
    [Fact]
    public void ARiverBankIsMeshedAndTheWaterDrawsNoWallAgainstIt()
    {
      const double bed = 20.0;
      const double bank = 48.3;
      const double waterline = 40.0;

      // Centre 32, half-width 8: the channel runs x 24..40, so both its banks fall on a chunk seam.
      var source = Surrounded((cx, cy) => TerrainFixtures.Shore(
        cx, cy, (worldX, _) => Math.Abs(worldX - 32) <= 8 ? bed : bank, waterline));

      var mesh = Mesh(source);

      Assert.NotNull(mesh.Water);
      Assert.NotEmpty(mesh.Water.Vertices);

      // Every water vertex sits at the waterline. A side wall puts them all the way down to the bed, and
      // this is the assertion that fails without the backing test in SurfaceNets.
      Assert.All(
        mesh.Water.Vertices,
        v => Assert.True(
          v.Y > waterline - 1.5f && v.Y < waterline + 1.5f,
          $"water vertex at y={v.Y} is not on the waterline of {waterline}"));

      // The geometric statement of the same thing, independent of where the fixture put its banks: no water
      // triangle may face sideways.
      foreach (var (a, b, c) in Triangles(mesh.Water))
      {
        var normal = (b - a).Cross(c - a);
        if (normal.LengthSquared() < 1e-12f)
        {
          continue;
        }

        normal = normal.Normalized();
        Assert.True(
          Math.Abs(normal.Y) > 0.7f,
          $"a water triangle faces sideways (normal {normal}), which is a wall rather than a surface");
      }

      // **The assertion that discriminates**, and the reason this test exists. Without
      // `TerrainPatch.MarkLateralMaterialFaces` this count is exactly zero: the band between the bed and
      // the waterline is in neither mask, so the terrain pass never visits it and the submerged bank is
      // not drawn at all. With it, this fixture yields several hundred. Measured both ways rather than
      // asserted as "not empty", because "not empty" would also pass on a single stray vertex.
      var bankVertices = mesh.Terrain.Vertices.Count(v => v.Y > bed + 1.0f && v.Y < waterline - 1.0f);

      Assert.True(
        bankVertices > 100,
        $"only {bankVertices} terrain vertices between the bed at {bed} and the waterline at {waterline}; " +
        "the submerged bank is not being meshed");
    }

    /// <summary>
    /// The water sheet reaches the shore rather than stopping a voxel short of it.
    /// </summary>
    /// <remarks>
    /// The regression guard for the dilated bed subtraction that used to run in <c>TerrainPatch</c>. Where the
    /// water was a voxel or two deep, the cell carrying its own top sheet was the same cell as the one
    /// carrying the bed, so subtracting the bed took the surface with it - and <c>BuildQuads</c> drops any
    /// quad with a missing corner without complaining. The waterline receded in a ragged one-voxel step.
    /// </remarks>
    [Fact]
    public void TheWaterSheetReachesTheShoreRatherThanStoppingShortOfIt()
    {
      const double waterline = 40.0;

      // A gentle ramp crossing the waterline at world x = 20.
      var source = Surrounded((cx, cy) => TerrainFixtures.Shore(
        cx, cy, (worldX, _) => 39.0 + (worldX - 20) * 0.25, waterline));

      var mesh = Mesh(source);

      Assert.NotNull(mesh.Water);
      var furthest = mesh.Water.Vertices.Max(v => v.X);

      Assert.True(
        furthest > 19.0f,
        $"the water sheet stops at x={furthest}, short of the shoreline at 20");
    }

    /// <summary>Triangles of a surface, as vertex triples.</summary>
    private static IEnumerable<(Vector3 A, Vector3 B, Vector3 C)> Triangles(ChunkSurface surface)
    {
      for (var i = 0; i + 2 < surface.Indices.Length; i += 3)
      {
        yield return (
          surface.Vertices[surface.Indices[i]],
          surface.Vertices[surface.Indices[i + 1]],
          surface.Vertices[surface.Indices[i + 2]]);
      }
    }

    /// <summary>
    /// The central claim: a surface written at a fractional elevation is drawn at that elevation.
    /// </summary>
    /// <remarks>
    /// This is what makes occupancy worth sending at all, and what rules out treating it as a density sampled at
    /// cell centres - that reading misplaces the surface and can never put it above <c>topCell + 0.5</c>.
    /// </remarks>
    [Theory]
    [InlineData(40.3)]
    [InlineData(40.9)]
    [InlineData(40.1)]
    [InlineData(12.5)]
    [InlineData(200.75)]
    public void ReconstructsFractionalSurfaceElevation(double surface)
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, surface));

      var mesh = Mesh(source);
      Assert.NotNull(mesh?.Terrain);

      var heights = Interior(mesh.Terrain);
      Assert.NotEmpty(heights);

      var expected = TerrainFixtures.Quantised(surface);

      // A tenth of a millimetre. The byte quantisation of occupancy is already accounted for by Quantised, so
      // what is left is float arithmetic in the interpolation and nothing else.
      Assert.All(heights, v => Assert.InRange(v.Y, expected - 0.0001, expected + 0.0001));
    }

    /// <summary>
    /// Two chunks side by side must cover the ground once each.
    /// </summary>
    /// <remarks>
    /// Vertices are legitimately duplicated at a seam - each chunk computes its own copy of the boundary cell's
    /// vertex - so comparing vertex extents proves nothing. What has to tile exactly is the quads: a duplicate is
    /// two surfaces fighting over the depth buffer, and a gap is a crack you can see the sky through.
    /// </remarks>
    [Fact]
    public void AdjacentChunksTileWithoutOverlapOrGap()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3), radius: 2);

      var centres = new List<(int X, int Y)>();

      foreach (var chunkX in new[] { 0, 1 })
      {
        var surface = Mesh(source, chunkX).Terrain;

        // Two triangles per quad sharing indices 0 and 2, so the four corners sit at 0, 1, 2 and 4.
        for (var i = 0; i + 5 < surface.Indices.Length; i += 6)
        {
          var a = surface.Vertices[surface.Indices[i]];
          var b = surface.Vertices[surface.Indices[i + 1]];
          var c = surface.Vertices[surface.Indices[i + 2]];
          var d = surface.Vertices[surface.Indices[i + 4]];

          centres.Add((
            (int)Math.Round((a.X + b.X + c.X + d.X) / 4.0),
            (int)Math.Round((a.Z + b.Z + c.Z + d.Z) / 4.0)));
        }
      }

      Assert.Equal(2 * Size * Size, centres.Count);
      Assert.Equal(centres.Count, centres.Distinct().Count());

      var columns = centres.Select(p => p.X).Distinct().OrderBy(x => x).ToList();

      Assert.Equal(Enumerable.Range(0, 2 * Size), columns);
    }

    /// <summary>
    /// Winding, against Godot's own rule rather than against an assumption about it.
    /// </summary>
    /// <remarks>
    /// Server space is right-handed with z up; Godot's is y up, and the mapping swaps two axes, which is a
    /// reflection and flips orientation. Godot then treats clockwise as front-facing, and its <c>Plane(a, b, c)</c>
    /// - what <c>GenerateNormals</c> uses - takes a correctly wound triangle's normal to be <c>(a-c) x (a-b)</c>.
    /// Two flips cancel, so the index order carries over unchanged; this is the test that says so rather than
    /// hoping. Failing it means terrain is visible from underneath and invisible from above.
    /// </remarks>
    [Fact]
    public void WindingMakesFlatGroundFrontFaceUpward()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3));
      var surface = Mesh(source).Terrain;

      for (var i = 0; i + 2 < surface.Indices.Length; i += 3)
      {
        var a = surface.Vertices[surface.Indices[i]];
        var b = surface.Vertices[surface.Indices[i + 1]];
        var c = surface.Vertices[surface.Indices[i + 2]];

        var geometric = (a - c).Cross(a - b);
        if (geometric.LengthSquared() < 1e-12f)
        {
          continue;
        }

        Assert.True(
          geometric.Normalized().Y > 0.99f,
          $"triangle {i / 3} faces {geometric.Normalized()}, expected up");
      }
    }

    [Fact]
    public void FlatGroundShadingNormalsPointUp()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3));
      var surface = Mesh(source).Terrain;

      Assert.All(surface.Normals, n => Assert.True(n.Y > 0.99f, $"normal {n} is not up"));
    }

    /// <summary>
    /// A cave has two surfaces facing opposite ways, and the field decides which without being told.
    /// </summary>
    /// <remarks>
    /// The occupancy fraction on the roof voxel means the same number as the one on the floor voxel, and nothing in
    /// the format says one fills upward and the other downward. Corner averaging infers it from the neighbours,
    /// which is the property that makes this method work on caves at all.
    /// </remarks>
    [Fact]
    public void CaveHasAFloorFacingUpAndACeilingFacingDown()
    {
      var source = Surrounded((x, y) => TerrainFixtures.WithCave(x, y, 60.0, floor: 20, roof: 30));
      var surface = Mesh(source).Terrain;

      var interior = surface.Vertices
        .Select((v, i) => (Vertex: v, Normal: surface.Normals[i]))
        .Where(p => p.Vertex.X > 2 && p.Vertex.X < Size - 2 && p.Vertex.Z > 2 && p.Vertex.Z < Size - 2)
        .ToList();

      var ceiling = interior.Where(p => p.Vertex.Y > 29.0 && p.Vertex.Y < 32.0).ToList();
      var floor = interior.Where(p => p.Vertex.Y > 19.0 && p.Vertex.Y < 21.0).ToList();

      Assert.NotEmpty(ceiling);
      Assert.NotEmpty(floor);

      Assert.All(ceiling, p => Assert.True(p.Normal.Y < -0.5f, $"ceiling normal {p.Normal} is not downward"));
      Assert.All(floor, p => Assert.True(p.Normal.Y > 0.5f, $"floor normal {p.Normal} is not upward"));
    }

    /// <summary>
    /// A chunk with nothing in it must cost nothing, which is what makes a 121-chunk view volume affordable.
    /// </summary>
    [Theory]
    [InlineData(TerrainFixtures.Granite, (byte)255)]
    [InlineData(TerrainFixtures.Air, (byte)0)]
    public void UniformChunkInUniformSurroundingsProducesNoMesh(byte block, byte occupancy)
    {
      var source = new FakeChunkSource();

      for (var chunkZ = -1; chunkZ <= 1; chunkZ++)
      {
        for (var chunkY = -1; chunkY <= 1; chunkY++)
        {
          for (var chunkX = -1; chunkX <= 1; chunkX++)
          {
            source.Put(TerrainFixtures.Uniform(chunkX, chunkY, chunkZ, block, occupancy));
          }
        }
      }

      Assert.True(Mesh(source).IsEmpty);
    }

    /// <summary>
    /// Terrain exactly on a chunk floor, which is the common case rather than an edge one.
    /// </summary>
    /// <remarks>
    /// Voxel index zero is sea level, so a coastal plain sits on the floor of chunk <c>z = 0</c> - a chunk of air
    /// over a chunk of rock, neither with any interior run boundary to mark. This rendered as nothing until
    /// <c>ChunkBands.SeamAtFloor</c> existed, and it would have been found by walking onto a beach rather than by
    /// reading the code.
    /// </remarks>
    [Fact]
    public void SurfaceOnAChunkFloorIsDrawnByTheChunkAbove()
    {
      var source = new FakeChunkSource();

      for (var chunkY = -1; chunkY <= 1; chunkY++)
      {
        for (var chunkX = -1; chunkX <= 1; chunkX++)
        {
          source.Put(TerrainFixtures.Uniform(chunkX, chunkY, 0, TerrainFixtures.Air, 0));
          source.Put(TerrainFixtures.Uniform(chunkX, chunkY, -1, TerrainFixtures.Granite, 255));
        }
      }

      var above = Mesh(source);
      Assert.NotNull(above?.Terrain);

      var heights = Interior(above.Terrain);
      Assert.NotEmpty(heights);
      Assert.All(heights, v => Assert.InRange(v.Y, -0.0001f, 0.0001f));

      // Each chunk owns the lattice edges at its own floor and not at its ceiling, so the chunk below must not
      // draw this same surface a second time. Empty rather than null: the mesher always returns a mesh now, so
      // that an empty one can still name what it was waiting on - here the rock slab at z = -2, which this
      // fixture does not hold.
      Assert.True(Mesh(source, 0, 0, -1).IsEmpty);
    }

    /// <summary>
    /// Open ocean at sea level: a chunk of air over a chunk of water, which is where a player spawns.
    /// </summary>
    /// <remarks>
    /// The real first-login case, and it exercises three things at once that no other test does together: the
    /// waterline sits exactly on a chunk floor, the vertex that carries it lives in a cell that is *air*, and the
    /// column has no solid material anywhere. If the material choice looked only at the cell holding the vertex
    /// it would colour the sea as air; if the terrain pass did not come back empty there would be a phantom
    /// seabed at the waterline.
    /// </remarks>
    [Fact]
    public void OpenOceanDrawsAWaterSurfaceAtSeaLevelAndNoTerrain()
    {
      var source = new FakeChunkSource();

      for (var chunkY = -1; chunkY <= 1; chunkY++)
      {
        for (var chunkX = -1; chunkX <= 1; chunkX++)
        {
          source.Put(TerrainFixtures.Uniform(chunkX, chunkY, 0, TerrainFixtures.Air, 0));
          source.Put(TerrainFixtures.Uniform(chunkX, chunkY, -1, TerrainFixtures.Water, 255));
        }
      }

      var mesh = Mesh(source);

      Assert.NotNull(mesh);
      Assert.Null(mesh.Terrain);
      Assert.NotNull(mesh.Water);

      var water = Interior(mesh.Water);
      Assert.NotEmpty(water);
      Assert.All(water, v => Assert.InRange(v.Y, -0.0001f, 0.0001f));

      // The vertex sits in an air cell with water below it, so the material has to come from a neighbour. Getting
      // this wrong paints the sea in whatever colour air is, which is how a blue ocean renders as nothing.
      var expected = TerrainFixtures.Appearance().ColourOf(TerrainFixtures.Water);

      for (var i = 0; i < mesh.Water.Vertices.Length; i++)
      {
        Assert.Equal(expected, mesh.Water.Colours[i]);
      }

      Assert.All(mesh.Water.Normals, n => Assert.True(n.Y > 0.99f, $"water normal {n} is not up"));
    }

    /// <summary>
    /// The sea arrives in two slabs, and the one that draws it is sent first.
    /// </summary>
    /// <remarks>
    /// The regression test for chunk-sized holes in the ocean. Open sea is uniform water under uniform air, so
    /// neither slab has an interior run boundary and the only thing that produces the water sheet is the seam at
    /// the air slab's own floor - which means the air slab cannot draw the sea until the water slab is in hand.
    ///
    /// <para>
    /// It never can be, first time round: the server offers a column's own anchor slab before the surface slabs
    /// under it, so the air slab is requested, sent and meshed first as a matter of course. The mesh that results
    /// is empty and <i>correct</i>, and the bug was that it was also silent - it recorded no dependency on the
    /// water below, so when the water landed nothing knew to look again and that column of sea stayed missing for
    /// the rest of the session.
    /// </para>
    ///
    /// <para>
    /// So the assertion that matters is the middle one. Emptiness is fine; emptiness owing nothing is the bug.
    /// </para>
    /// </remarks>
    [Fact]
    public void AnAirSlabMeshedBeforeItsWaterOwesTheWaterAndDrawsTheSeaOnceItArrives()
    {
      var source = new FakeChunkSource();

      for (var chunkY = -1; chunkY <= 1; chunkY++)
      {
        for (var chunkX = -1; chunkX <= 1; chunkX++)
        {
          source.Put(TerrainFixtures.Uniform(chunkX, chunkY, 0, TerrainFixtures.Air, 0));
        }
      }

      var dry = Mesh(source);

      Assert.True(dry.IsEmpty);
      Assert.Contains(new ChunkKey(0, 0, -1), dry.MissingNeighbours);

      for (var chunkY = -1; chunkY <= 1; chunkY++)
      {
        for (var chunkX = -1; chunkX <= 1; chunkX++)
        {
          source.Put(TerrainFixtures.Uniform(chunkX, chunkY, -1, TerrainFixtures.Water, 255));
        }
      }

      var wet = Mesh(source);

      Assert.NotNull(wet.Water);

      var water = Interior(wet.Water);
      Assert.NotEmpty(water);
      Assert.All(water, v => Assert.InRange(v.Y, -0.0001f, 0.0001f));
    }

    /// <summary>
    /// A flat chunk is one quad per column - the same geometry a heightfield mesher would emit.
    /// </summary>
    /// <remarks>
    /// The guard against the mask or the ownership bounds silently going wrong. Too many triangles means a chunk is
    /// meshing cells it does not own or is not skipping uniform runs; too few means it is dropping quads.
    /// </remarks>
    [Fact]
    public void FlatChunkIsOneQuadPerColumn()
    {
      var source = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3));
      var surface = Mesh(source).Terrain;

      Assert.Equal(2 * Size * Size, surface.TriangleCount);
      Assert.Equal((Size + 1) * (Size + 1), surface.Vertices.Length);
    }

    /// <summary>
    /// Water is its own surface, and the ground under it is still drawn.
    /// </summary>
    /// <remarks>
    /// Two runs of the same mesher over two masks, taken from the palette's <c>solid</c> flag rather than from
    /// hardcoded ids. A riverbed that vanished under water would be a hole to fall through as soon as the water
    /// material got a transparent shader.
    /// </remarks>
    [Fact]
    public void WaterIsASeparateSurfaceAboveTheTerrainSurface()
    {
      // A waterline well above the terrain's mean, so large areas are genuinely submerged rather than leaving a
      // handful of vertices in one puddle where the ground happens to dip below it.
      const int seaLevel = 50;

      var source = Surrounded(
        (x, y) => TerrainFixtures.Rolling(x, y, baseElevation: 40, waterLevel: seaLevel), radius: 2);

      var mesh = Mesh(source);

      Assert.NotNull(mesh?.Terrain);
      Assert.NotNull(mesh.Water);
      Assert.False(mesh.Water.IsEmpty);

      var waterline = TerrainFixtures.Quantised(seaLevel + TerrainFixtures.WaterFraction);

      var water = Interior(mesh.Water);
      Assert.NotEmpty(water);

      // Nothing above the waterline, and the flat top of the sea reaches it exactly.
      Assert.All(water, v => Assert.True(v.Y <= waterline + 0.0001, $"water at {v.Y} is above the waterline"));
      Assert.InRange(water.Max(v => v.Y), waterline - 0.0001, waterline + 0.0001);

      // The riverbed is still drawn under it. Dropping it would be a hole to fall through the moment the water
      // material becomes transparent.
      Assert.NotEmpty(Interior(mesh.Terrain));
    }

    /// <summary>
    /// Lava is a third surface, and it does not merge with water or with the ground.
    /// </summary>
    /// <remarks>
    /// The claim the whole <see cref="BlockAppearance.SurfaceKind"/> generalisation exists for. Before it there
    /// were exactly two masks and a bare <c>Surface == Terrain ? TerrainMask : WaterMask</c>, so a third kind
    /// would have landed silently on the water mask - and the failure is not a crash but a *plausible* picture: a
    /// lava lake touching a river, drawn as one translucent sheet in whichever colour won the vertex.
    ///
    /// <para>
    /// The fixture puts both fluids in one chunk on either side of a non-axis-aligned boundary, so a mesher that
    /// mixed the masks could not produce this geometry by accident.
    /// </para>
    ///
    /// <para>
    /// <b>There is deliberately no assertion about the terrain surface here</b>, and its absence is the point
    /// rather than an omission. This fixture submerges every column, and the materialiser fills everything below
    /// an air interface to full occupancy - bed and fluid alike - so a rock/fluid boundary is not an occupancy
    /// change and <see cref="ChunkBands"/> never marks it. The bed under a fluid is therefore not meshed, which
    /// is true of every lake bed in the world and is exactly why <see cref="BlockAppearance"/> draws lava
    /// opaque: an alpha would show a hole where the pool's basin should be. This test asserted
    /// <c>NotNull(mesh.Terrain)</c> until that was noticed, which was asserting the opposite of the design the
    /// renderer beside it depends on. <see cref="WaterIsASeparateSurfaceAboveTheTerrainSurface"/> is where the
    /// bed-under-water claim lives, and it holds there because that fixture has ground standing above the
    /// waterline as well as under it.
    /// </para>
    /// </remarks>
    [Fact]
    public void LavaIsItsOwnSurfaceAndDoesNotMergeWithWater()
    {
      const int ground = 40;
      const int lavaLevel = 43;
      const int waterLevel = 46;

      var source = Surrounded(
        (x, y) => TerrainFixtures.WithLavaPool(x, y, ground, lavaLevel, waterLevel, radius: 24.0), radius: 2);

      var mesh = Mesh(source);

      Assert.NotNull(mesh);
      Assert.NotNull(mesh.Lava);
      Assert.NotNull(mesh.Water);
      Assert.False(mesh.Lava.IsEmpty);
      Assert.False(mesh.Water.IsEmpty);

      // Each fluid is at its own level and neither reaches the other's. If they shared a mask this would be one
      // surface spanning both, so the two ranges would overlap.
      var lavaTop = TerrainFixtures.Quantised(lavaLevel + TerrainFixtures.LavaFraction);
      var waterTop = TerrainFixtures.Quantised(waterLevel + TerrainFixtures.WaterFraction);

      var lava = Interior(mesh.Lava);
      var water = Interior(mesh.Water);
      Assert.NotEmpty(lava);
      Assert.NotEmpty(water);

      Assert.All(lava, v => Assert.True(v.Y <= lavaTop + 0.0001, $"lava at {v.Y} is above its own surface"));
      Assert.True(
        water.Max(v => v.Y) > lavaTop + 0.5,
        "the water surface should stand well above the lava's, or the fixture is not separating them");

      Assert.InRange(lava.Max(v => v.Y), lavaTop - 0.0001, lavaTop + 0.0001);
      Assert.InRange(water.Max(v => v.Y), waterTop - 0.0001, waterTop + 0.0001);

      // The crater floor IS drawn under the lava, and for two versions of this test it was not: a bed-to-fluid
      // boundary changes the material and not the occupancy, and ChunkBands was built from occupancy alone, so
      // the cell was never visited. Teaching it to see the material change was the decision that fixed it - the
      // same one that put a bed under every lake and sea in the world.
      Assert.NotNull(mesh.Terrain);
      Assert.False(mesh.Terrain.IsEmpty);

      // And the floor stands below the lava it holds, rather than being the pool's own underside drawn twice.
      var floor = Interior(mesh.Terrain);
      Assert.NotEmpty(floor);
      Assert.True(
        floor.Min(v => v.Y) < lavaTop,
        "the crater floor should lie below the lava surface");
    }

    /// <summary>
    /// A sea bed is drawn under standing water, and the water is not given an underside on top of it.
    /// </summary>
    /// <remarks>
    /// The end-to-end form of the change: the fixture is faithful to what the materialiser writes for a submerged
    /// column - ground full to the brim, the fluid holding the only partial voxel - so the sand/water interface is
    /// a change of material and nothing else. Every sea floor and lake bed in the world used to be missing here,
    /// which left the sea a transparent sheet over empty space and nothing for a depth-graded shader to read.
    ///
    /// <para>
    /// The second half is what keeps the first from costing more than it buys. The interface bounds the ground and
    /// the water both, so drawing it from both passes would put a water underside exactly where the bed is - two
    /// coincident sheets, which is a doubly blended sea and, under an opaque pool, z-fighting. Every water vertex
    /// standing at the waterline is the assertion that it is drawn once.
    /// </para>
    /// </remarks>
    [Fact]
    public void ASeaBedIsDrawnUnderStandingWaterAndTheWaterKeepsNoUnderside()
    {
      const int bed = 20;
      const double waterline = 40.5;

      var source = Surrounded((x, y) => TerrainFixtures.Submerged(x, y, 0, bed, waterline), radius: 2);
      var mesh = Mesh(source);

      Assert.NotNull(mesh?.Terrain);
      Assert.NotNull(mesh.Water);

      var floor = Interior(mesh.Terrain);
      var surface = Interior(mesh.Water);

      Assert.NotEmpty(floor);
      Assert.NotEmpty(surface);

      // The bed stands where it was written, not at the waterline.
      Assert.All(floor, v => Assert.InRange(v.Y, bed - 0.5001f, bed + 0.5001f));

      // And the water is its own top sheet only.
      var top = (float)TerrainFixtures.Quantised(waterline);
      Assert.All(
        surface, v => Assert.True(v.Y > bed + 1.0f, $"water vertex at {v.Y} is down at the bed, not the surface"));
      Assert.InRange(surface.Max(v => v.Y), top - 0.0001, top + 0.0001);
    }

    /// <summary>
    /// Water a voxel deep - which is what a shoreline is - is a top sheet and nothing else.
    /// </summary>
    /// <remarks>
    /// <see cref="ASeaBedIsDrawnUnderStandingWaterAndTheWaterKeepsNoUnderside"/> asks the same question of a bed
    /// twenty metres down, and passed throughout while every beach in the world was drawn wrong. That is the
    /// whole reason this one exists: down there the bed cells are nowhere near the waterline, so the occupancy
    /// mask never reaches them and the fluid pass never visits them. <see cref="TerrainFixtures.Shore"/> puts
    /// the bed one cell under the surface, where a beach puts it, and the mask reaches it on its own - through
    /// <c>ChunkBands</c>'s one-cell reach and its horizontal-face pass both.
    ///
    /// <para>
    /// The fluid pass then found the crossing there, because the ground is masked out of its field and the bed
    /// is therefore a genuine sign change for it, and gave the water an underside exactly on top of the sea
    /// floor with a rim joining the two. A closed shell, coincident with an opaque surface, triangulated
    /// differently because it was built from a different cell: a doubly blended sea z-fighting its own bed,
    /// which is what put a band of dark triangles along the water's edge. Every vertex standing at the waterline
    /// is the assertion that the shell is gone - an underside would sit down on the bed and a rim half way
    /// between.
    /// </para>
    /// </remarks>
    [Fact]
    public void WaterAVoxelDeepOverAShoreIsATopSheetAndNothingElse()
    {
      const double waterline = 40.0;

      // One in ten, so a single chunk holds water two voxels deep, the one-voxel band, and dry sand above it.
      var source = Surrounded(
        (x, y) => TerrainFixtures.Shore(x, y, (worldX, _) => waterline + (worldX - 20) * 0.1, waterline),
        radius: 2);

      var mesh = Mesh(source);

      Assert.NotNull(mesh?.Water);
      Assert.NotNull(mesh.Terrain);

      var surface = Interior(mesh.Water);
      var ground = Interior(mesh.Terrain);

      Assert.NotEmpty(surface);
      Assert.All(surface, v => Assert.InRange(v.Y, waterline - 0.0001, waterline + 0.0001));

      // And the bed under it is still drawn - by the pass that owns it, which is the other half of the bargain.
      Assert.Contains(ground, v => v.Y < waterline - 0.5);
    }

    /// <summary>The lava surface is flat and faces up, like any other standing fluid.</summary>
    /// <remarks>
    /// Cheap, and it is the assertion that catches lava being meshed with the winding or the normals of a
    /// *ceiling* - which is a real possibility, because a fluid sheet has solid ground above nothing and the
    /// vertex that carries it sits in a cell that is air.
    /// </remarks>
    [Fact]
    public void LavaNormalsPointUp()
    {
      var source = Surrounded(
        (x, y) => TerrainFixtures.WithLavaPool(x, y, ground: 40, lavaLevel: 44, waterLevel: 0, radius: 64.0),
        radius: 2);

      var mesh = Mesh(source);

      Assert.NotNull(mesh?.Lava);
      Assert.False(mesh.Lava.IsEmpty);
      Assert.All(mesh.Lava.Normals, n => Assert.True(n.Y > 0.99f, $"lava normal {n} is not up"));

      var expected = TerrainFixtures.Appearance().ColourOf(TerrainFixtures.Lava);
      Assert.All(mesh.Lava.Colours, c => Assert.Equal(expected, c));
    }

    /// <summary>
    /// A chunk meshed before its neighbours arrive says which ones it had to guess about.
    /// </summary>
    /// <remarks>
    /// That list is what lets the renderer re-mesh only the chunks actually waiting on an arrival instead of a
    /// whole 3x3 block around it. An empty list where a neighbour is missing would leave a permanent flat seam.
    /// </remarks>
    [Fact]
    public void MissingNeighboursAreReported()
    {
      var alone = new FakeChunkSource();
      alone.Put(TerrainFixtures.Flat(0, 0, 0, 40.3));

      var mesh = Mesh(alone);

      Assert.NotNull(mesh);
      Assert.Equal(8, mesh.MissingNeighbours.Length);

      var surrounded = Surrounded((x, y) => TerrainFixtures.Flat(x, y, 0, 40.3));

      Assert.Empty(Mesh(surrounded).MissingNeighbours);
    }
  }
}
