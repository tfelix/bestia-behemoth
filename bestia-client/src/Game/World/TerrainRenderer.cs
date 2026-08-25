using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Keeps a <see cref="MeshInstance3D"/> per chunk in step with what <see cref="ClientChunkStore"/> holds.
  /// </summary>
  /// <remarks>
  /// Meshing runs on the thread pool and installing runs on the main thread, because that split is the difference
  /// between terrain appearing and the game stuttering while it does. A login streams a whole view volume, and
  /// <c>BnetSocket</c> drains its receive queue in one frame, so without a budget somewhere the first second of
  /// play is one long hitch. There are two budgets here for two different costs: <see cref="MeshJobs"/> bounds how
  /// much CPU is spent meshing at once, and <see cref="InstallsPerFrame"/> bounds the main thread's share, which is
  /// the <c>ArrayMesh</c> and its upload to the GPU.
  ///
  /// <para>
  /// Nothing here touches a Godot resource off the main thread. <see cref="SurfaceNets"/> returns plain arrays of
  /// value types, and every <c>ArrayMesh</c>, <c>MeshInstance3D</c> and collision shape is created in
  /// <see cref="_Process"/>.
  /// </para>
  ///
  /// <para><b>Re-meshing.</b> A chunk is meshed when it lands, and again whenever a neighbour it had to guess about
  /// arrives - <see cref="ChunkMesh.MissingNeighbours"/> says which those are, so an arrival only disturbs the
  /// chunks that were actually waiting on it rather than a whole 3x3 block.
  /// </para>
  ///
  /// <para>
  /// <b>Both ends of that have to be checked, because a job in flight owns no tile.</b>
  /// <see cref="Invalidate"/> can only reach chunks that are already installed, and meshing is asynchronous
  /// while decoding is not - so a dependency that arrives between a job starting and its result installing
  /// announces itself to nobody. <see cref="InstallOne"/> therefore asks the reverse question as it records the
  /// debt: is any of it already paid? Without that, the window is wide enough to matter on every login and every
  /// teleport, since <see cref="InstallsPerFrame"/> is deliberately smaller than the decode budget feeding it.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class TerrainRenderer : Node3D
  {
    /// <summary>
    /// How many chunks may be meshed concurrently.
    /// </summary>
    /// <remarks>
    /// Below the core count on purpose. The zone connection, the decoder and Godot's own servers all want a core,
    /// and terrain arriving a tenth of a second later is invisible next to a dropped frame.
    /// </remarks>
    [Export] public int MeshJobs { get; set; } = 3;

    /// <summary>
    /// Finished meshes handed to the scene per frame.
    /// </summary>
    /// <remarks>
    /// Two is about a third of a millisecond of main-thread work at a typical chunk size, and clears a 121-chunk
    /// view volume in a second - comfortably faster than the server's own four-chunks-per-tick send budget
    /// delivers it, so this is not what makes terrain appear slowly.
    /// </remarks>
    [Export] public int InstallsPerFrame { get; set; } = 2;

    /// <summary>
    /// Radius in chunks, around the player's own, within which chunks get a collision body.
    /// </summary>
    /// <remarks>
    /// Two gives a 5x5 block, and because the player can be standing at their own chunk's edge the guaranteed
    /// reach is the radius alone - 64 m at 32 m chunks, not the 160 m the block spans. That covers click-to-move
    /// picking and the camera's spring arm; collision for the whole streamed disc would be a triangle soup per
    /// chunk for terrain nobody will touch.
    /// </remarks>
    [Export] public int CollisionRadiusChunks { get; set; } = 2;

    /// <summary>
    /// Material for the opaque terrain surface. Left unset, a default that honours vertex colour is built.
    /// </summary>
    /// <remarks>
    /// The default is not a nicety. Godot's fallback material ignores vertex colour entirely, and vertex colour
    /// is currently where every scrap of terrain colour lives - so an unset material does not render plain
    /// terrain, it renders a white world. Nothing in the scene wires these up, because the renderer is created
    /// in code rather than placed in <c>Game.tscn</c>.
    /// </remarks>
    [Export] public Material TerrainMaterial { get; set; }

    /// <summary>Material for the water surface. Left unset, a transparent default is built.</summary>
    [Export] public Material WaterMaterial { get; set; }

    /// <summary>Material for the lava surface. Left unset, an opaque emissive default is built.</summary>
    [Export] public Material LavaMaterial { get; set; }

    /// <summary>
    /// The fallback: vertex colour as albedo, and rough.
    /// </summary>
    /// <remarks>
    /// No longer what terrain is normally drawn with - <see cref="TerrainMaterials"/> loads the shader for that -
    /// but kept as what happens when the shader will not load, and deliberately so. An unassigned material does
    /// not render plain terrain, it renders nothing, and a missing world is a far worse first symptom of a typo
    /// in a shader than a world that has gone back to looking flat.
    ///
    /// <para>
    /// Built once and shared by every chunk either way, so the whole terrain is one material and Godot can batch
    /// it.
    /// </para>
    /// </remarks>
    private static Material DefaultTerrainMaterial() => new StandardMaterial3D
    {
      VertexColorUseAsAlbedo = true,
      Roughness = 1.0f,

      // Fully matte, and that is the point rather than a taste call. The scene's sky is near-white, and any
      // specular on a large flat surface reflects it straight back into the camera - which reads as "the material
      // did not apply" because the result is indistinguishable from Godot's white fallback.
      MetallicSpecular = 0.0f,
      SpecularMode = BaseMaterial3D.SpecularModeEnum.Disabled
    };

    /// <summary>
    /// Water: vertex colour again, but transparent and visible from underneath.
    /// </summary>
    /// <remarks>
    /// Back-face culling is off because the surface of a lake is a single sheet with no underside of its own,
    /// and a swimming player looking up at it would otherwise see straight through into the sky. The alpha comes
    /// from the palette's own colour for <c>WATER</c>, so the depth of the tint is a data question rather than a
    /// shader one.
    /// </remarks>
    private static Material DefaultWaterMaterial() => new StandardMaterial3D
    {
      VertexColorUseAsAlbedo = true,
      Transparency = BaseMaterial3D.TransparencyEnum.Alpha,
      CullMode = BaseMaterial3D.CullModeEnum.Disabled,

      // Matte for now, for the same reason as the terrain. A shiny sheet of water is the right look eventually,
      // but it belongs to a shader that controls its own reflection rather than to a StandardMaterial3D mirroring
      // a placeholder procedural sky - which turns the sea white and hides whether anything else is wrong.
      Roughness = 1.0f,
      MetallicSpecular = 0.0f,
      SpecularMode = BaseMaterial3D.SpecularModeEnum.Disabled
    };

    /// <summary>
    /// Lava: opaque and emissive, unlike water.
    /// </summary>
    /// <remarks>
    /// Drawing it opaque is load bearing rather than a taste call - see <see cref="Mesh.BlockAppearance"/>'s
    /// <c>Molten</c>, which explains why the basin under a pool is not meshed and what an alpha would show
    /// through to.
    ///
    /// <para>
    /// The emission makes the surface glow; it does not light the scene. Without SDFGI or a light baked beside
    /// the pool, the rock around a lava flow stays as dark as the rest of the cave, which looks like the
    /// emission failed and is not that. A real lava light is a job for whoever does the shader pass.
    /// </para>
    ///
    /// <para>
    /// Back-face culling is off for water's reason: a pool surface is a single sheet with no underside, and a
    /// player looking at it from below or from inside would see straight through it.
    /// </para>
    /// </remarks>
    private static Material DefaultLavaMaterial() => new StandardMaterial3D
    {
      VertexColorUseAsAlbedo = true,
      EmissionEnabled = true,
      Emission = new Color(1.0f, 0.34f, 0.06f),
      EmissionEnergyMultiplier = 2.0f,
      CullMode = BaseMaterial3D.CullModeEnum.Disabled,
      Roughness = 1.0f,
      MetallicSpecular = 0.0f,
      SpecularMode = BaseMaterial3D.SpecularModeEnum.Disabled
    };

    private sealed class Tile
    {
      /// <summary>One node per <see cref="Mesh.BlockAppearance.SurfaceKind"/>, null where the chunk has none.</summary>
      internal readonly MeshInstance3D[] Surfaces = new MeshInstance3D[BlockAppearance.SurfaceKinds];

      internal StaticBody3D Body;
      internal CollisionShape3D Shape;

      /// <summary>Vertices and indices of the terrain surface, kept so a collider can be built later.</summary>
      internal Vector3[] CollisionFaces;

      internal ChunkKey[] MissingNeighbours = Array.Empty<ChunkKey>();
    }

    private readonly Dictionary<ChunkKey, Tile> _tiles = new();

    /// <summary>Chunks waiting to be meshed, nearest first is not attempted - the store already arrives that way.</summary>
    private readonly Queue<ChunkKey> _pending = new();

    private readonly HashSet<ChunkKey> _queued = new();

    /// <summary>Finished meshes waiting for a frame to be installed in. Written by worker threads.</summary>
    private readonly System.Collections.Concurrent.ConcurrentQueue<ChunkMesh> _finished = new();

    private ClientChunkStore _store;
    private float _voxelSize = 1.0f;
    private int _chunkSize = 32;
    private int _chunkHeight = 256;

    /// <summary>The world's chunk grid, so locally derived addresses fold across a seam the way the
    /// server's already have. <see cref="ChunkWrap.None"/> until configured.</summary>
    private ChunkWrap _wrap = ChunkWrap.None;

    private int _running;

    private ChunkKey _collisionAnchor;
    private bool _hasAnchor;

    private TerrainMaterials _materials;

    /// <summary>Whether the debug shader is currently the one terrain is drawn with.</summary>
    private bool _debugShading;

    /// <summary>So the unconfigured complaint is made once rather than sixty times a second.</summary>
    private bool _warnedUnconfigured;

    /// <summary>Surface kinds already described in the log, so the diagnostic prints twice and not 242 times.</summary>
    private readonly HashSet<string> _described = new();

    public int TileCount => _tiles.Count;

    public int PendingCount => _pending.Count;

    /// <summary>
    /// Points the renderer at a store and tells it the world's dimensions.
    /// </summary>
    /// <remarks>
    /// Everything already meshed is discarded. This is called on a world info message, which means either a fresh
    /// login or a reconnect, and in both cases the voxel size and chunk dimensions the existing meshes were built
    /// against can no longer be assumed.
    /// </remarks>
    public void Configure(ClientChunkStore store, WorldInfoSMSG worldInfo)
    {
      _store = store;

      _materials ??= TerrainMaterials.Load();

      // Null when the shader would not compile, which deliberately leaves the flat vertex-colour material in
      // place rather than an unassigned one - see TerrainMaterials.
      TerrainMaterial ??= _materials?.Shipping ?? DefaultTerrainMaterial();
      WaterMaterial ??= DefaultWaterMaterial();
      LavaMaterial ??= DefaultLavaMaterial();

      if (worldInfo != null)
      {
        _voxelSize = (float)worldInfo.VoxelSizeMetres;
        _chunkSize = worldInfo.ChunkSize;
        _chunkHeight = worldInfo.ChunkHeight;
        _wrap = ChunkWrap.Of(worldInfo);
      }

      Clear();
    }

    public void Clear()
    {
      foreach (var tile in _tiles.Values)
      {
        FreeSurfaces(tile);
        tile.Body?.QueueFree();
      }

      _tiles.Clear();
      _pending.Clear();
      _queued.Clear();

      while (_finished.TryDequeue(out _))
      {
        // Results for a world that no longer exists.
      }
    }

    /// <summary>
    /// Queues a chunk for meshing, along with anything that was waiting on it.
    /// </summary>
    /// <remarks>
    /// Called when a chunk is decoded or patched. The neighbour sweep is what closes the seam left by meshing a
    /// chunk before its neighbours arrived: a mesh records which positions it had to extend a boundary into, and
    /// this is where that debt is paid.
    /// </remarks>
    public void Invalidate(ChunkKey key)
    {
      Enqueue(key);

      foreach (var (other, tile) in _tiles)
      {
        foreach (var missing in tile.MissingNeighbours)
        {
          if (missing.Equals(key))
          {
            Enqueue(other);
            break;
          }
        }
      }
    }

    /// <summary>Drops a chunk's geometry, for a manifest that took it out of the subscribed set.</summary>
    public void Remove(ChunkKey key)
    {
      if (!_tiles.Remove(key, out var tile))
      {
        return;
      }

      FreeSurfaces(tile);
      tile.Body?.QueueFree();
    }

    private static void FreeSurfaces(Tile tile)
    {
      foreach (var surface in tile.Surfaces)
      {
        surface?.QueueFree();
      }
    }

    /// <summary>
    /// Tells the renderer where the player is, so collision follows them.
    /// </summary>
    /// <remarks>
    /// In chunk coordinates rather than metres, because that is the granularity at which anything changes and it
    /// makes the common call - the player moved a metre - a comparison of three ints and nothing else.
    /// </remarks>
    public void SetCollisionAnchor(ChunkKey anchor)
    {
      if (_hasAnchor && _collisionAnchor.Equals(anchor))
      {
        return;
      }

      _collisionAnchor = anchor;
      _hasAnchor = true;

      // The same signal serves the shader's floating-point origin. It is not about collision, but it wants
      // exactly this: somewhere near the player, updated rarely, in whole chunk steps.
      _materials?.SetUvAnchor(new Vector3(
        anchor.X * _chunkSize * _voxelSize,
        anchor.Z * _chunkHeight * _voxelSize,
        anchor.Y * _chunkSize * _voxelSize));

      foreach (var (key, tile) in _tiles)
      {
        SyncCollision(key, tile);
      }
    }

    private void Enqueue(ChunkKey key)
    {
      if (_queued.Add(key))
      {
        _pending.Enqueue(key);
      }
    }

    public override void _Process(double delta)
    {
      // Polled rather than handled in _UnhandledInput, matching the HUD windows: several of them are separate
      // Windows with their own viewports, so an unhandled-input override does not see the key while one is open.
      if (Input.IsActionJustPressed(ToggleDebugShading))
      {
        SetDebugShading(!_debugShading);
      }

      StartJobs();
      Install();
    }

    private static readonly StringName ToggleDebugShading = "toggle_terrain_debug";

    /// <summary>
    /// Swaps the whole world between the shipping shader and its debug twin.
    /// </summary>
    /// <remarks>
    /// One material is shared by every chunk, so this is two assignments and a walk of the existing instances
    /// rather than anything per-chunk - and because both materials wrap the same include, what the debug views
    /// show is what the shipping shader computed rather than a second implementation of it.
    /// </remarks>
    private void SetDebugShading(bool enabled)
    {
      var material = enabled ? _materials?.Debug : _materials?.Shipping;

      if (material == null)
      {
        return;
      }

      _debugShading = enabled;
      TerrainMaterial = material;

      foreach (var tile in _tiles.Values)
      {
        var surface = tile.Surfaces[(int)BlockAppearance.SurfaceKind.Terrain];

        if (surface != null)
        {
          surface.MaterialOverride = material;
        }
      }

      GD.Print($"[terrain] debug shading {(enabled ? "on" : "off")}");
    }

    private void StartJobs()
    {
      // An unconfigured renderer used to discard the whole queue here without a word, which is how it managed to
      // draw nothing at all while the log showed chunks arriving and decoding perfectly. Say it once, loudly.
      if (_store == null && _pending.Count > 0 && !_warnedUnconfigured)
      {
        _warnedUnconfigured = true;
        GD.PushError(
          $"[terrain] {_pending.Count} chunks are queued but Configure() was never called, so none of them " +
          "can be meshed. The renderer was attached without the world info being replayed to it.");
      }

      while (_running < Math.Max(1, MeshJobs) && _pending.Count > 0)
      {
        var key = _pending.Dequeue();
        _queued.Remove(key);

        if (_store?.Get(key) == null)
        {
          // Dropped between being queued and being picked up, by a reset manifest or a divergent patch.
          continue;
        }

        var source = _store;
        var voxelSize = _voxelSize;
        var wrap = _wrap;

        // Incremented with an interlock even though only this thread increments it: the workers decrement it, and a
        // plain read-modify-write here could lose one of those and leak a slot until the next world.
        System.Threading.Interlocked.Increment(ref _running);

        Task.Run(() =>
        {
          try
          {
            // An empty result is still a result - it means whatever used to be here must come down - and the
            // mesher returns one rather than null so that an empty mesh still carries what it was waiting on.
            // Substituting a debt-free mesh here is how a chunk of sea that meshed before its water arrived
            // became a hole nothing would ever revisit.
            _finished.Enqueue(SurfaceNets.Build(source, key, BlockAppearance.Current, voxelSize, wrap));
          }
          catch (Exception ex)
          {
            GD.PushError($"[terrain] meshing {key} failed: {ex}");
          }
          finally
          {
            System.Threading.Interlocked.Decrement(ref _running);
          }
        });
      }
    }

    private void Install()
    {
      var budget = Math.Max(1, InstallsPerFrame);

      for (var done = 0; done < budget && _finished.TryDequeue(out var mesh); done++)
      {
        // The chunk may have gone away, or been superseded by a patch that queued a newer job, while this was in
        // flight. Either way the store is the authority on whether it is still wanted.
        if (_store?.Get(mesh.Key) == null)
        {
          Remove(mesh.Key);
          continue;
        }

        InstallOne(mesh);
      }
    }

    private void InstallOne(ChunkMesh mesh)
    {
      if (!_tiles.TryGetValue(mesh.Key, out var tile))
      {
        tile = new Tile();
        _tiles[mesh.Key] = tile;
      }

      tile.MissingNeighbours = mesh.MissingNeighbours ?? Array.Empty<ChunkKey>();

      // The debt may already be paid. `Invalidate` can only disturb chunks that have a tile, and this chunk had
      // none while its job was in flight - so a neighbour that arrived in that window told nobody, and no later
      // arrival will mention it again. Installing is the first moment this dependency is on the books, which
      // makes it the right moment to ask whether it is still outstanding.
      foreach (var missing in tile.MissingNeighbours)
      {
        if (_store?.Get(missing) != null)
        {
          Enqueue(mesh.Key);
          break;
        }
      }

      for (var kind = 0; kind < tile.Surfaces.Length; kind++)
      {
        var surfaceKind = (BlockAppearance.SurfaceKind)kind;
        tile.Surfaces[kind] = Apply(
          tile.Surfaces[kind], mesh.Surfaces[kind], MaterialFor(surfaceKind),
          $"{surfaceKind.ToString().ToLowerInvariant()} {mesh.Key}"
        );
      }

      // Only the terrain surface collides, and lava is deliberately in the same boat as water rather than in
      // terrain's. Walking into it is refused server-side by `WalkableTile`, which knows lava is BLOCKED; a
      // collider here would instead stop the camera's spring arm at the surface of every pool, and make the
      // pool a clickable floor the pathfinder then refuses - a worse lie than no floor at all.
      tile.CollisionFaces = mesh.Terrain == null || mesh.Terrain.IsEmpty ? null : FacesOf(mesh.Terrain);

      // A rebuilt mesh invalidates whatever shape was there, so drop it and let the sync decide afresh.
      tile.Shape?.QueueFree();
      tile.Shape = null;

      SyncCollision(mesh.Key, tile);
    }

    /// <summary>
    /// The material one surface is drawn with.
    /// </summary>
    /// <remarks>
    /// The throwing default is not defensive padding. C# does not check a switch expression over an enum for
    /// exhaustiveness, so a fourth <see cref="Mesh.BlockAppearance.SurfaceKind"/> would otherwise be drawn with
    /// whichever material fell out of the last arm - which renders, and renders wrong.
    /// </remarks>
    private Material MaterialFor(BlockAppearance.SurfaceKind kind) => kind switch
    {
      BlockAppearance.SurfaceKind.Terrain => TerrainMaterial,
      BlockAppearance.SurfaceKind.Water => WaterMaterial,
      BlockAppearance.SurfaceKind.Lava => LavaMaterial,
      _ => throw new ArgumentOutOfRangeException(nameof(kind), kind, "no material for this surface")
    };

    /// <summary>
    /// Attaches the per-vertex slot weights to the surface arrays and returns the format flags describing them.
    /// </summary>
    /// <remarks>
    /// The flags argument is not optional the way it looks. Godot reads the *presence* of a custom channel from
    /// the array being non-null, but the *layout* only from these bits, and the default of zero means
    /// <c>RGBA8_UNORM</c>'s neighbour in the enum rather than "work it out" - so omitting them decodes eight
    /// bytes of weights as something else entirely.
    ///
    /// <para>
    /// <b>The length check behind this is fatal rather than lossy.</b> An eight-bit custom channel must be a byte
    /// array of exactly four per vertex; anything else fails the surface's own validation and <c>ArrayMesh</c>
    /// adds <i>no surface at all</i>. The chunk then has a mesh with zero surfaces, which draws nothing - so the
    /// symptom of getting this wrong is invisible terrain, not wrong terrain, and it looks identical to the mesher
    /// having returned nothing. That is what the surface count in the diagnostic below is for.
    /// </para>
    ///
    /// <para>
    /// Water and lava carry weights they will never use, because they run through the same mesher and sixteen
    /// bytes on a sheet of water is cheaper than a second code path. This still guards on null rather than assuming, so
    /// that a surface built before this existed - or by a test that does not care - is drawn instead of dropped.
    /// </para>
    /// </remarks>
    private static Godot.Mesh.ArrayFormat SlotWeightFormat(ChunkSurface surface, Godot.Collections.Array arrays)
    {
      // All four or none. A partial set would upload weights the shader reads past the end of, and the slots in
      // the missing channels would come back as whatever the attribute defaults to - which draws *something*,
      // and is therefore worse than drawing nothing.
      if (surface.SlotWeights0 == null || surface.SlotWeights1 == null ||
          surface.SlotWeights2 == null || surface.SlotWeights3 == null)
      {
        return 0;
      }

      arrays[(int)Godot.Mesh.ArrayType.Custom0] = surface.SlotWeights0;
      arrays[(int)Godot.Mesh.ArrayType.Custom1] = surface.SlotWeights1;
      arrays[(int)Godot.Mesh.ArrayType.Custom2] = surface.SlotWeights2;
      arrays[(int)Godot.Mesh.ArrayType.Custom3] = surface.SlotWeights3;

      const uint Rgba8 = (uint)Godot.Mesh.ArrayCustomFormat.Rgba8Unorm;

      return (Godot.Mesh.ArrayFormat)(
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom0Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom1Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom2Shift) |
        (Rgba8 << (int)Godot.Mesh.ArrayFormat.FormatCustom3Shift));
    }

    private MeshInstance3D Apply(MeshInstance3D instance, ChunkSurface surface, Material material, string name)
    {
      if (surface == null || surface.IsEmpty)
      {
        instance?.QueueFree();
        return null;
      }

      var arrays = new Godot.Collections.Array();
      arrays.Resize((int)Godot.Mesh.ArrayType.Max);
      arrays[(int)Godot.Mesh.ArrayType.Vertex] = surface.Vertices;
      arrays[(int)Godot.Mesh.ArrayType.Normal] = surface.Normals;
      arrays[(int)Godot.Mesh.ArrayType.Color] = surface.Colours;
      arrays[(int)Godot.Mesh.ArrayType.Index] = surface.Indices;

      var format = SlotWeightFormat(surface, arrays);

      var arrayMesh = new ArrayMesh();
      arrayMesh.AddSurfaceFromArrays(Godot.Mesh.PrimitiveType.Triangles, arrays, null, null, format);

      if (instance == null)
      {
        instance = new MeshInstance3D { Name = name };
        AddChild(instance);
      }

      instance.Mesh = arrayMesh;

      if (material != null)
      {
        instance.MaterialOverride = material;
      }

      // Once per surface kind. The three ways this goes wrong look like two things on screen - white terrain or
      // no terrain - and each has causes fixed in completely different files: the material not applying, the
      // vertex data being wrong, or the surface having been rejected outright for a malformed custom channel.
      // Printing all of it together tells them apart without another round trip.
      if (_described.Add(name.Split(' ')[0]))
      {
        var vertexColour = surface.Colours.Length > 0 ? surface.Colours[0].ToString() : "none";
        var albedo = material is BaseMaterial3D standard
          ? $"vertexColorAsAlbedo={standard.VertexColorUseAsAlbedo} albedo={standard.AlbedoColor}"
          : material?.GetType().Name ?? "NO MATERIAL";

        // Zero surfaces means AddSurfaceFromArrays refused the arrays - which renders exactly like a chunk that
        // was never meshed, so without this the next hour goes on the mesher rather than on the array shapes.
        var weights = surface.SlotWeights0 == null
          ? "none"
          : $"{surface.SlotWeights0.Length}+{surface.SlotWeights1.Length}+{surface.SlotWeights2?.Length}+" +
            $"{surface.SlotWeights3?.Length}B for {surface.Vertices.Length} verts";

        GD.Print(
          $"[terrain] {name.Split(' ')[0]} surface: {surface.TriangleCount} tris in " +
          $"{arrayMesh.GetSurfaceCount()} mesh surface(s), first vertex colour {vertexColour}, " +
          $"slot weights {weights}, material {albedo}");
      }

      return instance;
    }

    /// <summary>Expands an indexed surface into the flat triangle list a concave shape wants.</summary>
    private static Vector3[] FacesOf(ChunkSurface surface)
    {
      var faces = new Vector3[surface.Indices.Length];

      for (var i = 0; i < surface.Indices.Length; i++)
      {
        faces[i] = surface.Vertices[surface.Indices[i]];
      }

      return faces;
    }

    /// <summary>
    /// Adds or removes a chunk's collider according to how far it is from the player.
    /// </summary>
    /// <remarks>
    /// The body is kept once created and only its shape comes and goes, because a <c>StaticBody3D</c> is cheap and
    /// churning nodes as a player walks back and forth across a boundary is not.
    /// </remarks>
    private void SyncCollision(ChunkKey key, Tile tile)
    {
      var wanted = tile.CollisionFaces != null && _hasAnchor && WithinCollisionRange(key);

      if (!wanted)
      {
        tile.Shape?.QueueFree();
        tile.Shape = null;
        return;
      }

      if (tile.Shape != null)
      {
        return;
      }

      tile.Body ??= NewBody(key);

      var shape = new ConcavePolygonShape3D();
      shape.SetFaces(tile.CollisionFaces);

      tile.Shape = new CollisionShape3D { Shape = shape };
      tile.Body.AddChild(tile.Shape);
    }

    /// <summary>
    /// The script every clickable floor in the game wears, loaded once.
    /// </summary>
    /// <remarks>
    /// Reused rather than reimplemented, because "walkable floor" is already a contract with two halves and
    /// missing either one fails quietly in a different way. It puts the body in the <c>floor</c> group, which is
    /// what <c>MouseManager.get_floor_hit_at_mouse</c> filters on - a collider outside the group is hit by the ray
    /// and then discarded, so the ground cursor flickers rather than disappearing outright. And it relays
    /// <c>input_event</c> to the mouse state machine, which is how a click becomes a move order.
    /// </remarks>
    private static readonly GDScript WalkableFloor =
      GD.Load<GDScript>("res://Game/Ground/walkable_floor.gd");

    private StaticBody3D NewBody(ChunkKey key)
    {
      var body = new StaticBody3D { Name = $"collision {key}" };

      // Before AddChild, so the script's own _ready runs with the script attached and the group is joined.
      body.SetScript(WalkableFloor);

      AddChild(body);

      // Physics picking is a signal on the body, and the scene file wires this by hand for the placeholder
      // ground. Bodies made in code have to do it themselves or clicks land on nothing.
      body.Connect(
        CollisionObject3D.SignalName.InputEvent,
        new Callable(body, "_on_input_event"));

      return body;
    }

    private bool WithinCollisionRange(ChunkKey key)
    {
      var radius = Math.Max(0, CollisionRadiusChunks);

      // Measured the short way round, or the chunk immediately east of a player standing at the seam reads
      // as the full width of the world away and never gets a collider.
      return Math.Abs(_wrap.DeltaX(_collisionAnchor.X, key.X)) <= radius &&
             Math.Abs(_wrap.DeltaY(_collisionAnchor.Y, key.Y)) <= radius &&
             Math.Abs(key.Z - _collisionAnchor.Z) <= 1;
    }

    /// <summary>
    /// Moves the collision anchor to whichever chunk contains a Godot world position.
    /// </summary>
    /// <remarks>
    /// The convenience the player-tracking caller actually wants, so nothing outside this class has to know the
    /// chunk dimensions or that the vertical axis is swapped between the server and Godot. Cheap enough to call
    /// every frame: it resolves to three integer divisions and, unless the player crossed a boundary, a comparison.
    /// </remarks>
    public void SetCollisionAnchorAt(Vector3 position) => SetCollisionAnchor(ChunkAt(position));

    /// <summary>The chunk containing a Godot world position.</summary>
    public ChunkKey ChunkAt(Vector3 position)
    {
      // Godot (x, y, z) is the server's (x, z, y): its vertical axis is the server's z, whose zero is sea level.
      var voxelX = (long)Mathf.Floor(position.X / _voxelSize);
      var voxelY = (long)Mathf.Floor(position.Z / _voxelSize);
      var voxelZ = (long)Mathf.Floor(position.Y / _voxelSize);

      // Folded, because this is derived from a scene position rather than received: a player standing in the
      // last column names the chunk past the seam as readily as any other, and the tile it wants is the one
      // the server sent under the canonical address.
      return _wrap.Normalise(new ChunkKey(
        FloorDiv(voxelX, _chunkSize),
        FloorDiv(voxelY, _chunkSize),
        FloorDiv(voxelZ, _chunkHeight)));
    }

    private static int FloorDiv(long value, int divisor)
    {
      var quotient = value / divisor;

      if (value % divisor != 0 && (value < 0) != (divisor < 0))
      {
        quotient--;
      }

      return (int)quotient;
    }

    public string Summary() =>
      $"terrain: {_tiles.Count} tiles, {_pending.Count} queued, {_running} meshing";
  }
}
