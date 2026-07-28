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

    /// <summary>
    /// Vertex colour as albedo, and rough: this is rock, soil and grass rather than anything polished.
    /// </summary>
    /// <remarks>
    /// Built once and shared by every chunk, so the whole terrain is one material and Godot can batch it.
    /// Replacing this with a <c>Texture2DArray</c> blended per vertex is the intended next step, and the vertex
    /// data already carries what that needs - see <see cref="Mesh.BlockAppearance"/>.
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

    private sealed class Tile
    {
      internal MeshInstance3D Terrain;
      internal MeshInstance3D Water;
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
    private int _running;

    private ChunkKey _collisionAnchor;
    private bool _hasAnchor;

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

      TerrainMaterial ??= DefaultTerrainMaterial();
      WaterMaterial ??= DefaultWaterMaterial();

      if (worldInfo != null)
      {
        _voxelSize = (float)worldInfo.VoxelSizeMetres;
        _chunkSize = worldInfo.ChunkSize;
        _chunkHeight = worldInfo.ChunkHeight;
      }

      Clear();
    }

    public void Clear()
    {
      foreach (var tile in _tiles.Values)
      {
        tile.Terrain?.QueueFree();
        tile.Water?.QueueFree();
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

      tile.Terrain?.QueueFree();
      tile.Water?.QueueFree();
      tile.Body?.QueueFree();
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
      StartJobs();
      Install();
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

        // Incremented with an interlock even though only this thread increments it: the workers decrement it, and a
        // plain read-modify-write here could lose one of those and leak a slot until the next world.
        System.Threading.Interlocked.Increment(ref _running);

        Task.Run(() =>
        {
          try
          {
            var mesh = SurfaceNets.Build(source, key, BlockAppearance.Current, voxelSize);

            // An empty result is still a result: it means whatever used to be here must come down.
            _finished.Enqueue(mesh ?? new ChunkMesh
            {
              Key = key,
              MissingNeighbours = Array.Empty<ChunkKey>()
            });
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

      tile.Terrain = Apply(tile.Terrain, mesh.Terrain, TerrainMaterial, $"terrain {mesh.Key}");
      tile.Water = Apply(tile.Water, mesh.Water, WaterMaterial, $"water {mesh.Key}");

      // Only the opaque surface collides. Swimming through water is a server-side check against its own voxels,
      // and a collider on the waterline would stop the camera's spring arm at the surface of every pond.
      tile.CollisionFaces = mesh.Terrain == null || mesh.Terrain.IsEmpty ? null : FacesOf(mesh.Terrain);

      // A rebuilt mesh invalidates whatever shape was there, so drop it and let the sync decide afresh.
      tile.Shape?.QueueFree();
      tile.Shape = null;

      SyncCollision(mesh.Key, tile);
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

      var arrayMesh = new ArrayMesh();
      arrayMesh.AddSurfaceFromArrays(Godot.Mesh.PrimitiveType.Triangles, arrays);

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

      // Once per surface kind. "Coloured geometry renders white" has two causes that look identical on screen -
      // the material not applying, or the vertex colours themselves being wrong - and they are fixed in completely
      // different places. Printing both together tells them apart without another round trip.
      if (_described.Add(name.Split(' ')[0]))
      {
        var vertexColour = surface.Colours.Length > 0 ? surface.Colours[0].ToString() : "none";
        var albedo = material is BaseMaterial3D standard
          ? $"vertexColorAsAlbedo={standard.VertexColorUseAsAlbedo} albedo={standard.AlbedoColor}"
          : material?.GetType().Name ?? "NO MATERIAL";

        GD.Print(
          $"[terrain] {name.Split(' ')[0]} surface: {surface.TriangleCount} tris, " +
          $"first vertex colour {vertexColour}, material {albedo}");
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

      return Math.Abs(key.X - _collisionAnchor.X) <= radius &&
             Math.Abs(key.Y - _collisionAnchor.Y) <= radius &&
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

      return new ChunkKey(
        FloorDiv(voxelX, _chunkSize),
        FloorDiv(voxelY, _chunkSize),
        FloorDiv(voxelZ, _chunkHeight));
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
