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

    [Export] public Material TerrainMaterial { get; set; }

    [Export] public Material WaterMaterial { get; set; }

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
    private BlockAppearance _appearance;
    private float _voxelSize = 1.0f;
    private int _chunkSize = 32;
    private int _chunkHeight = 256;
    private int _running;

    private ChunkKey _collisionAnchor;
    private bool _hasAnchor;

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
    public void Configure(ClientChunkStore store, WorldInfoSMSG worldInfo, BlockPaletteSMSG palette)
    {
      _store = store;
      _appearance = BlockAppearance.From(palette);

      if (worldInfo != null)
      {
        _voxelSize = (float)worldInfo.VoxelSizeMetres;
        _chunkSize = worldInfo.ChunkSize;
        _chunkHeight = worldInfo.ChunkHeight;
      }

      Clear();
    }

    /// <summary>Updates the palette without discarding meshes, for a palette that arrives after the world info.</summary>
    public void SetPalette(BlockPaletteSMSG palette)
    {
      _appearance = BlockAppearance.From(palette);

      // Colours come from the palette, so everything already built is drawn with the fallback. Re-mesh rather than
      // leave a world in placeholder grey; on a normal login this queue is empty because the palette lands first.
      foreach (var key in _tiles.Keys)
      {
        Enqueue(key);
      }
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
        var appearance = _appearance;
        var voxelSize = _voxelSize;

        // Incremented with an interlock even though only this thread increments it: the workers decrement it, and a
        // plain read-modify-write here could lose one of those and leak a slot until the next world.
        System.Threading.Interlocked.Increment(ref _running);

        Task.Run(() =>
        {
          try
          {
            var mesh = SurfaceNets.Build(source, key, appearance, voxelSize);

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

    private StaticBody3D NewBody(ChunkKey key)
    {
      var body = new StaticBody3D { Name = $"collision {key}" };
      AddChild(body);

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
