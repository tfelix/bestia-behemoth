using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Draws the static entities of each held chunk: trees, mana crystals, wound spires, landmarks.
  /// </summary>
  /// <remarks>
  /// A sibling of <see cref="TerrainRenderer"/> and it keeps the same contract: one node per chunk, removed
  /// when the chunk leaves the manifest. That is the only lifecycle rule there is - the server sends a batch
  /// behind each chunk payload and never sends a removal for an individual entity, so a chunk going away is
  /// what takes its contents with it.
  ///
  /// <para>
  /// <b>A prop is an entity with a visual on it, drawn without being one.</b> The scenes under
  /// <c>Game/Entity/Visual/</c> are what a prop looks like, and a kind with art gets one instantiated per
  /// prop - so a tree here is the same <c>TreeVisual</c> an entity would carry. What it deliberately does
  /// <i>not</i> get is the rest of an entity: props never go through <c>EntityManager</c>, which instantiates
  /// a full <c>Entity.tscn</c> - health bar, nameplate, chat bubble, damage numbers, movement prediction -
  /// per entity, and whose <c>get_closest_entity</c> is a linear scan written on the assumption that entity
  /// counts are small. A view volume holds one to seven thousand trees.
  /// </para>
  ///
  /// <para>
  /// <b>The cost of that is real and worth stating.</b> A scene instance per prop is a node per prop, where a
  /// <see cref="MultiMesh"/> would be one draw call per kind per chunk with the transforms in a buffer. Kinds
  /// with no art still take the multimesh path, so only trees pay it today. If a dense wood turns out to cost
  /// too much, the fix is a <c>MultiMesh</c> built from the visual scene's own mesh for kinds that are a
  /// single static <c>MeshInstance3D</c> - which is a change to <see cref="AddSceneProp"/> and
  /// <see cref="PropAppearance"/>, not to anything upstream of them.
  /// </para>
  ///
  /// <para>
  /// <b>Collectible kinds take a third path, and leave the multimesh to do it.</b> A kind that
  /// <see cref="PropAppearance.Kind.Collectible"/> marks gets one <see cref="Node3D"/> per prop, holding the
  /// same shared placeholder mesh plus a <c>PropPicker</c> area carrying the entity id a click needs. That
  /// looks like giving up the batching, and it is - but the batching was already spent: a
  /// <see cref="MultiMesh"/> instance is not pickable at all, so a clickable prop needs a collision node
  /// regardless, and a <see cref="MultiMesh"/> cannot delete an arbitrary instance (only truncate the tail via
  /// <c>VisibleInstanceCount</c>). Keeping both would mean zero-scaling a collected crystal's transform,
  /// leaving a hole in the buffer, and maintaining an <c>entityId -> slot</c> index to find it by. One node
  /// per prop is fewer moving parts for a population two orders of magnitude below the trees.
  /// </para>
  ///
  /// <para>
  /// Trees and the artless non-collectible kinds are untouched: still a scene instance and a multimesh
  /// respectively, and still not clickable. Felling one of those goes through damage, not a click.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class StaticEntityRenderer : Node3D
  {
    /// <summary>
    /// Chunk -> the one node holding everything drawn for it.
    /// </summary>
    /// <remarks>
    /// A container per chunk rather than a list of nodes, so that removing a chunk is a single
    /// <c>QueueFree</c> whichever mix of scene instances and multimeshes it happens to hold.
    /// </remarks>
    private readonly Dictionary<ChunkKey, Node3D> _byChunk = new();

    /// <summary>Kind -> its visual scene, loaded once. Only holds kinds that have art.</summary>
    private readonly Dictionary<int, PackedScene> _scenes = new();

    /// <summary>Kind -> its placeholder mesh, built once. Only holds kinds that do not.</summary>
    /// <remarks>
    /// <c>Godot.Mesh</c> spelled in full, and it has to be: this file's own namespace has a
    /// <c>BestiaBehemothClient.Game.World.Mesh</c> in it - the surface-nets code in <c>Game/World/Mesh/</c> -
    /// which shadows the Godot type and makes the bare name resolve to a namespace.
    /// </remarks>
    private readonly Dictionary<int, Godot.Mesh> _placeholders = new();

    /// <summary>
    /// Chunk -> entity id -> the node drawn for that collectible prop, so one can be removed on its own.
    /// </summary>
    /// <remarks>
    /// A drawing ledger, not a second copy of the world. It answers only "which node did I draw for this id";
    /// what actually stands in a column is <c>ChunkStreamManager</c>'s retained batch, which is pruned in step
    /// with the manifest and is the replay source when a renderer attaches late. Keeping entries here instead
    /// would mean a removal had to be applied to both, or a re-attach would redraw a crystal already collected.
    /// </remarks>
    private readonly Dictionary<ChunkKey, Dictionary<long, Node3D>> _collectibleNodes = new();

    /// <summary>
    /// The script every collectible prop's click target wears, loaded once.
    /// </summary>
    /// <remarks>
    /// The <see cref="TerrainRenderer"/> precedent, for the same reason: the behaviour is a contract - relay
    /// <c>input_event</c> to the mouse state machine, and carry the entity id - and it belongs in one place
    /// rather than being rebuilt per prop from C#.
    /// </remarks>
    private static readonly GDScript PropPicker =
      GD.Load<GDScript>("res://Game/World/prop_picker.gd");

    /// <summary>
    /// Metres per voxel, so that a prop stands where the ground it was grounded against was drawn.
    /// </summary>
    /// <remarks>
    /// The wire carries voxel coordinates and <see cref="TerrainRenderer"/> scales them by this to reach
    /// Godot units, so anything placed on that terrain has to apply the same factor. Heights and radii do
    /// not: they arrive in metres already, and a Godot unit is a metre.
    /// </remarks>
    private float _voxelSize = 1.0f;

    /// <summary>
    /// The terrain a prop is standing on, so its base can be put where that terrain is drawn.
    /// </summary>
    /// <remarks>
    /// See <see cref="GroundedPositionOf"/>. Null in a headless test and until the first
    /// <see cref="Configure"/>, which just means props stand at the height the server sent.
    /// </remarks>
    private ClientChunkStore _store;

    /// <summary>Chunk dimensions, needed to address a voxel from a global coordinate.</summary>
    private int _chunkSize = 32;

    private int _chunkHeight = 256;

    /// <summary>The world's chunk grid, so a prop next to a seam finds the ground under it.</summary>
    private ChunkWrap _wrap = ChunkWrap.None;

    /// <summary>
    /// Adopts the world's units and the terrain to stand props on. Safe to call with nulls, which keeps the
    /// defaults.
    /// </summary>
    /// <remarks>
    /// Separate from the constructor for the reason <see cref="TerrainRenderer.Configure"/> is: the server
    /// sends <c>WorldInfoSMSG</c> the instant a connection authenticates, which is during master selection,
    /// and the Game scene that owns this renderer does not exist until a master has been chosen. So this is
    /// always called after that message has come and gone, by <c>ChunkStreamManager</c>'s own setter.
    /// </remarks>
    public void Configure(ClientChunkStore store, WorldInfoSMSG worldInfo)
    {
      _store = store;

      if (worldInfo != null)
      {
        _voxelSize = (float)worldInfo.VoxelSizeMetres;
        _chunkSize = worldInfo.ChunkSize;
        _chunkHeight = worldInfo.ChunkHeight;
        _wrap = ChunkWrap.Of(worldInfo);
      }

      Clear();
    }

    /// <summary>
    /// Where a prop stands, in Godot units, with its base on the terrain as drawn rather than as rounded.
    /// </summary>
    /// <remarks>
    /// A prop's z comes off the same <c>ChunkCoords.standingZ</c> an entity's does, so it carries the same
    /// whole-voxel rounding and a tree ends up hovering or half-buried by up to half a metre. Unlike an entity
    /// this is asked once, at placement: a prop does not move, so there is nothing to smooth and nothing to do
    /// per frame.
    ///
    /// <para>
    /// Falls back to the height the server sent when the terrain under the prop is not held. That is not a
    /// hypothetical - a batch can be applied before its own chunk has been decoded - which is why
    /// <c>ChunkStreamManager</c> holds a batch back until the ground is there.
    /// </para>
    ///
    /// <para>
    /// Horizontally the prop is put in the middle of its voxel, not on the corner its coordinate names - a cell
    /// spans <c>[n, n+1]</c>, so a tree placed at the bare coordinate stands on the seam between four of them.
    /// The same offset <c>Entity._apply_position</c> applies, and for the same reason; see <c>TileSpace</c>.
    /// </para>
    /// </remarks>
    private Vector3 GroundedPositionOf(ChunkStaticEntitiesSMSG.Entry entry)
    {
      // Entry.Position is already in Godot's axis order, so y is the vertical one and z is the server's y - which
      // is why the half-voxel centring goes on x and z and the vertical is left to the surface probe below.
      var centre = (Vector3)entry.Position + new Vector3(0.5f, 0f, 0.5f);
      var placed = centre * _voxelSize;

      if (_store == null)
      {
        return placed;
      }

      var surface = Mesh.SurfaceProbe.SurfaceAt(
        _store, Mesh.BlockAppearance.Current,
        centre.X, centre.Z, entry.Position.Y, _chunkSize, _chunkHeight, _wrap);

      return double.IsNaN(surface)
        ? placed
        : new Vector3(placed.X, (float)(surface * _voxelSize), placed.Z);
    }

    /// <summary>
    /// Replaces whatever was drawn for this chunk.
    /// </summary>
    /// <remarks>
    /// Replaces rather than merges, because a batch is always the whole truth about a column: the server sends
    /// one when a client is given the terrain and does not send incremental changes to it. So a second batch for
    /// a chunk means the column was re-materialised, and the right response is to throw away what was there.
    /// </remarks>
    public void Apply(ChunkStaticEntitiesSMSG batch)
    {
      Remove(batch.Key);

      if (batch.Entries.Count == 0)
      {
        return;
      }

      var container = new Node3D { Name = $"Chunk_{batch.Key.X}_{batch.Key.Y}" };
      AddChild(container);
      _byChunk[batch.Key] = container;

      // Only allocated if this chunk actually holds an artless kind, which a wood never does.
      Dictionary<int, List<ChunkStaticEntitiesSMSG.Entry>> batched = null;

      foreach (var entry in batch.Entries)
      {
        var appearance = PropAppearance.Of(entry.Kind);

        if (appearance.HasScene)
        {
          AddSceneProp(container, entry, appearance);
          continue;
        }

        if (appearance.Collectible)
        {
          AddCollectibleProp(container, batch.Key, entry, appearance);
          continue;
        }

        batched ??= new Dictionary<int, List<ChunkStaticEntitiesSMSG.Entry>>();
        if (!batched.TryGetValue(entry.Kind, out var list))
        {
          list = new List<ChunkStaticEntitiesSMSG.Entry>();
          batched[entry.Kind] = list;
        }
        list.Add(entry);
      }

      if (batched == null)
      {
        return;
      }

      foreach (var (kind, entries) in batched)
      {
        AddPlaceholderBatch(container, kind, entries);
      }
    }

    /// <summary>
    /// Instantiates one visual scene for a prop that has art.
    /// </summary>
    /// <remarks>
    /// Scaled <b>uniformly</b>, unlike <see cref="AddPlaceholderBatch"/>'s y-only scaling. A placeholder is a
    /// unit box whose width means nothing, so stretching it vertically is the only sensible reading of a
    /// height; a real model is proportioned, and the generator draws a tree's trunk height and crown radius
    /// off the same roll specifically so that a big tree has a big crown. Scaling one axis would give a tall
    /// tree a sapling's canopy.
    /// </remarks>
    private void AddSceneProp(Node3D container, ChunkStaticEntitiesSMSG.Entry entry, PropAppearance.Kind appearance)
    {
      var scene = SceneFor(entry.Kind, appearance);
      if (scene == null)
      {
        return;
      }

      var node = scene.Instantiate<Node3D>();

      var scale = appearance.NaturalHeight > 0f ? entry.Height / appearance.NaturalHeight : 1f;
      var basis = new Basis(Vector3.Up, entry.Yaw).Scaled(new Vector3(scale, scale, scale));

      node.Transform = new Transform3D(basis, GroundedPositionOf(entry));
      container.AddChild(node);
    }

    /// <summary>
    /// Draws one collectible prop as its own node, with a click target on it.
    /// </summary>
    /// <remarks>
    /// The mesh is the *same shared instance* every prop of this kind uses, straight out of
    /// <see cref="PlaceholderFor"/> - a <c>MeshInstance3D</c> holds a reference, so a hundred crystals cost one
    /// mesh between them and the y-scale that makes each one its own height lives on the node's transform.
    ///
    /// <para>
    /// The pick box is at least <c>MinPickWidth</c> across whatever the drawn box measures, because a 0.3 m
    /// shard at any distance is otherwise a target nobody can hit. It is deliberately not the server's
    /// <c>collider</c> block from <c>prop-kinds.yml</c>: that one is for movement, which wants the real
    /// extent, and this one is for clicking, which wants a generous one.
    /// </para>
    ///
    /// <para>
    /// The y-scale that makes each prop its own height lives on the <b>mesh node</b>, not on the prop root, so
    /// that the <see cref="Area3D"/> hangs off an unscaled parent and its box is sized in plain metres. A
    /// collision shape under a non-uniformly scaled ancestor is a shape Godot has to warn about and physics
    /// has to special-case; the shape is only three floats, so there is nothing to gain by scaling it.
    /// </para>
    /// </remarks>
    private void AddCollectibleProp(
      Node3D container, ChunkKey key, ChunkStaticEntitiesSMSG.Entry entry, PropAppearance.Kind appearance)
    {
      var prop = new Node3D
      {
        Name = $"prop {entry.EntityId}",
        Transform = new Transform3D(new Basis(Vector3.Up, entry.Yaw), GroundedPositionOf(entry))
      };

      // Scaled on y only, exactly as AddPlaceholderBatch does and for the same reason: the box is a unit cube
      // standing on its own origin, so this makes a tall thing tall rather than also fat.
      prop.AddChild(new MeshInstance3D
      {
        Mesh = PlaceholderFor(entry.Kind),
        Scale = new Vector3(1f, entry.Height, 1f)
      });

      var area = new Area3D { Name = "Picker" };

      // Before AddChild, so the script is attached by the time the node enters the tree - the same ordering
      // TerrainRenderer.NewBody documents.
      area.SetScript(PropPicker);

      var width = Mathf.Max(appearance.PlaceholderWidth, MinPickWidth);
      var shape = new CollisionShape3D
      {
        Shape = new BoxShape3D { Size = new Vector3(width, entry.Height, width) },
        // The prop grows upward from its own origin, so the box's centre sits at half its height.
        Position = new Vector3(0f, entry.Height * 0.5f, 0f)
      };

      area.AddChild(shape);
      prop.AddChild(area);

      area.Set("entity_id", entry.EntityId);
      area.Set("kind", entry.Kind);

      // Physics picking is a signal on the collision object, and a node built in code has to wire it itself.
      area.Connect(CollisionObject3D.SignalName.InputEvent, new Callable(area, "_on_input_event"));

      container.AddChild(prop);

      if (!_collectibleNodes.TryGetValue(key, out var byId))
      {
        byId = new Dictionary<long, Node3D>();
        _collectibleNodes[key] = byId;
      }

      byId[entry.EntityId] = prop;
    }

    /// <summary>
    /// Draws every prop of one artless kind in this chunk as a single multimesh.
    /// </summary>
    /// <remarks>
    /// Scaled on y only for most kinds: the mesh is a box of the kind's own width standing on its own origin,
    /// so this makes a tall thing tall rather than also fat. See <see cref="AddSceneProp"/> for why a real
    /// model is treated differently.
    ///
    /// <para>
    /// A building is the exception, and it is scaled on all three axes because its footprint arrives per entry
    /// - see <see cref="ChunkStaticEntitiesSMSG.Entry.HasFootprint"/>. The x and z factors are *relative to the
    /// kind's own placeholder width*, since the shared mesh is already that wide; a building whose row said 5 m
    /// and whose lot is 9 m across is scaled by 1.8 rather than by 9. Keeping the shared mesh is what lets a
    /// whole town's houses stay one multimesh instead of one draw each.
    /// </para>
    /// </remarks>
    private void AddPlaceholderBatch(Node3D container, int kind, List<ChunkStaticEntitiesSMSG.Entry> entries)
    {
      var multi = new MultiMesh
      {
        TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
        Mesh = PlaceholderFor(kind),
        InstanceCount = entries.Count
      };

      // The width the shared mesh was built at, which every per-entry footprint is expressed against. Guarded
      // because a row with a zero width would otherwise divide by it.
      var meshWidth = Mathf.Max(PropAppearance.Of(kind).PlaceholderWidth, 0.001f);

      for (var i = 0; i < entries.Count; i++)
      {
        var entry = entries[i];

        // Local x is the facing axis, so half-length scales x and half-width scales z. Doubled because the
        // wire carries half-extents and the mesh is a full width.
        var along = entry.HasFootprint ? entry.HalfLength * 2f / meshWidth : 1f;
        var across = entry.HasFootprint ? entry.HalfWidth * 2f / meshWidth : 1f;

        // The server's z is the ground the prop stands on, and a mesh built upward from its own origin wants
        // that as its base.
        var basis = new Basis(Vector3.Up, entry.Yaw).Scaled(new Vector3(along, entry.Height, across));
        multi.SetInstanceTransform(i, new Transform3D(basis, GroundedPositionOf(entry)));
      }

      container.AddChild(new MultiMeshInstance3D { Multimesh = multi });
    }

    /// <summary>
    /// Un-draws one collectible prop, leaving the rest of its chunk alone.
    /// </summary>
    /// <remarks>
    /// Surgical rather than re-running <see cref="Apply"/> for the chunk, because <see cref="Apply"/> replaces
    /// the whole container: collecting one crystal would free and re-instantiate every <c>TreeVisual</c> in
    /// the column, which is a visible hitch for a change to a single node.
    ///
    /// <para>
    /// Idempotent, which matters more than it looks. A prop that was promoted before being collected carries a
    /// <c>Dirtyable</c> <c>Health</c>, so its destruction also emits the ordinary entity vanish alongside our
    /// map-channel removal - and a chunk that unloads between the two calls has already taken the node.
    /// </para>
    /// </remarks>
    /// <returns>true if a node was actually freed</returns>
    public bool RemoveEntity(ChunkKey key, long entityId)
    {
      if (!_collectibleNodes.TryGetValue(key, out var byId) || !byId.Remove(entityId, out var node))
      {
        return false;
      }

      if (byId.Count == 0)
      {
        _collectibleNodes.Remove(key);
      }

      if (IsInstanceValid(node))
      {
        node.QueueFree();
      }

      return true;
    }

    /// <summary>Drops everything drawn for a chunk. Safe to call for a chunk that was never drawn.</summary>
    public void Remove(ChunkKey key)
    {
      // Dropped wholesale rather than per node: the container's own QueueFree below already takes the prop
      // nodes with it, so this is only the ledger catching up.
      _collectibleNodes.Remove(key);

      if (!_byChunk.Remove(key, out var container))
      {
        return;
      }

      if (IsInstanceValid(container))
      {
        container.QueueFree();
      }
    }

    public void Clear()
    {
      foreach (var key in new List<ChunkKey>(_byChunk.Keys))
      {
        Remove(key);
      }
    }

    /// <summary>The visual scene for a kind, loaded once, or null if it could not be loaded.</summary>
    /// <remarks>
    /// A failed load is cached as null too. Retrying it per prop would put a file-not-found error on the log
    /// for every tree in the view rather than once for the kind.
    /// </remarks>
    private PackedScene SceneFor(int kind, PropAppearance.Kind appearance)
    {
      if (_scenes.TryGetValue(kind, out var cached))
      {
        return cached;
      }

      var scene = ResourceLoader.Load<PackedScene>(appearance.ScenePath);
      if (scene == null)
      {
        GD.PushError($"[props] kind {kind} names {appearance.ScenePath}, which did not load; it will not be drawn.");
      }

      _scenes[kind] = scene;
      return scene;
    }

    /// <summary>A placeholder mesh for a kind with no art, built once.</summary>
    /// <remarks>
    /// Unit height, because <see cref="AddPlaceholderBatch"/> scales by the prop's own height.
    /// </remarks>
    private Godot.Mesh PlaceholderFor(int kind)
    {
      if (_placeholders.TryGetValue(kind, out var cached))
      {
        return cached;
      }

      var appearance = PropAppearance.Of(kind);

      var mesh = new BoxMesh
      {
        Size = new Vector3(appearance.PlaceholderWidth, 1f, appearance.PlaceholderWidth),
        Material = new StandardMaterial3D { AlbedoColor = appearance.PlaceholderColour }
      };

      _placeholders[kind] = mesh;
      return mesh;
    }

    /// <summary>
    /// Floor on a pick box's width, in metres, so the smallest props stay clickable.
    /// </summary>
    /// <remarks>
    /// A small mana crystal is drawn 0.3 m across. At the camera's usual distance that is a few pixels, and a
    /// target you have to aim at is worse than one that is slightly bigger than it looks. Nothing is lost by
    /// being generous: overlapping pick boxes still resolve to the nearest, and the server checks range anyway.
    /// </remarks>
    private const float MinPickWidth = 0.6f;
  }
}
