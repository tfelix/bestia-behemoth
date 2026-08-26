using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Draws the static entities of each held chunk: trees, ground cover, mana crystals, wound spires, landmarks.
  /// </summary>
  /// <remarks>
  /// A sibling of <see cref="TerrainRenderer"/> and it keeps the same contract: one node per chunk, removed
  /// when the chunk leaves the manifest, which is what takes a chunk's contents with it.
  ///
  /// <para>
  /// That used to be the <i>only</i> lifecycle rule, on the claim that the server never removes an individual
  /// entity. It does: <see cref="Bnet.Message.Map.StaticEntityRemovedSMSG"/> is how a felled tree or a picked
  /// herb goes away without its chunk being withdrawn, and <c>BnetSocket</c> has dispatched it for some time.
  /// </para>
  ///
  /// <para>
  /// <b>A prop is an entity with a visual on it, drawn without being one.</b> The scenes under
  /// <c>Game/Entity/Visual/</c> are what a prop looks like, and a kind with a scene gets one instantiated per
  /// prop - so a tree here is the same <c>TreeVisual</c> an entity would carry. What it deliberately does
  /// <i>not</i> get is the rest of an entity: props never go through <c>EntityManager</c>, which instantiates
  /// a full <c>Entity.tscn</c> - health bar, nameplate, chat bubble, damage numbers, movement prediction -
  /// per entity, and whose <c>get_closest_entity</c> is a linear scan written on the assumption that entity
  /// counts are small. A view volume holds one to seven thousand trees.
  /// </para>
  ///
  /// <para>
  /// <b>Everything that is not a tree is batched, art or no art.</b> A kind with a
  /// <see cref="PropAppearance.Kind.MeshPath"/> is drawn as its own mesh in a <see cref="MultiMesh"/>, and a
  /// kind with neither path is drawn as a placeholder box in exactly the same one - the two differ only in
  /// which mesh goes in and how the transform is scaled. So a chunk costs one draw call per kind, whether
  /// that kind is a thousand tufts of grass or a dozen crystals.
  /// </para>
  ///
  /// <para>
  /// <b>That batching is recent, and the ground cover is what forced it.</b> Collectible kinds used to take a
  /// third path - one <c>Node3D</c>, one <c>MeshInstance3D</c> and one <c>PropPicker</c> area each - on the
  /// argument that the batching was already spent, since a <see cref="MultiMesh"/> instance is not pickable
  /// and a clickable prop needs a collision node regardless. That was a fair trade at one collectible every
  /// 145 m. The ground cover kinds are collectible and run at about twice the tree density, so a view volume
  /// holds on the order of a thousand of them, and a thousand draw calls for the layer the player is least
  /// likely to be looking at is not a trade at all.
  /// </para>
  ///
  /// <para>
  /// <b>What a collectible kind still pays is one <see cref="Area3D"/> per prop, and no more.</b> The mesh
  /// left, the picker stayed: <c>MouseManager</c>'s pending collect uses <c>is_instance_valid</c> on the
  /// picker as its liveness test and <c>global_position</c> as the place to walk to, so a picker is a node
  /// per prop by contract and not by accident. Consolidating those into one area per kind per chunk, with the
  /// click resolved through <c>shape_idx</c> against a slot table, is the next thing here that would pay -
  /// but it is a change to the GDScript collect flow rather than to this file, and it buys a node where the
  /// batching bought a draw call.
  /// </para>
  ///
  /// <para>
  /// Removing one prop out of a batch is what <see cref="Drawn"/> is for. A <see cref="MultiMesh"/> cannot
  /// delete an arbitrary instance - only truncate the tail via <c>VisibleInstanceCount</c> - so a collected
  /// plant's slot is parked at a zero basis and the hole is left in the buffer. That is cheap precisely
  /// because the ledger which finds the slot has to exist anyway, to find the picker.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class StaticEntityRenderer : Node3D
  {
    /// <summary>
    /// What was drawn for one prop, so that prop can be taken away on its own.
    /// </summary>
    /// <remarks>
    /// Both halves are optional and a prop has at least one of them: a tree is a <see cref="Node"/> with no
    /// batch, an ordinary shrub is a batch slot with a picker node, and a wound spire is a batch slot with no
    /// node at all because nothing can click it.
    /// </remarks>
    private readonly struct Drawn
    {
      /// <summary>The scene instance, or the picker area, or null.</summary>
      public Node3D Node { get; init; }

      /// <summary>The multimesh this prop has a slot in, or null for a prop drawn as a scene.</summary>
      public MultiMesh Batch { get; init; }

      /// <summary>Which slot, meaningless without <see cref="Batch"/>.</summary>
      public int Slot { get; init; }
    }

    /// <summary>
    /// Chunk -> the one node holding everything drawn for it.
    /// </summary>
    /// <remarks>
    /// A container per chunk rather than a list of nodes, so that removing a chunk is a single
    /// <c>QueueFree</c> whichever mix of scene instances, multimeshes and pickers it happens to hold.
    /// </remarks>
    private readonly Dictionary<ChunkKey, Node3D> _byChunk = new();

    /// <summary>Kind -> its visual scene, loaded once. Only holds kinds that have one.</summary>
    private readonly Dictionary<int, PackedScene> _scenes = new();

    /// <summary>Kind -> its batched art mesh, loaded once. Only holds kinds that have one.</summary>
    private readonly Dictionary<int, Godot.Mesh> _art = new();

    /// <summary>Kind -> the material its art is drawn with, built once. Null for a kind that keeps the mesh's.</summary>
    private readonly Dictionary<int, Material> _materials = new();

    /// <summary>Kind -> its placeholder mesh, built once. Only holds kinds with no art at all.</summary>
    /// <remarks>
    /// <c>Godot.Mesh</c> spelled in full, and it has to be: this file's own namespace has a
    /// <c>BestiaBehemothClient.Game.World.Mesh</c> in it - the surface-nets code in <c>Game/World/Mesh/</c> -
    /// which shadows the Godot type and makes the bare name resolve to a namespace.
    /// </remarks>
    private readonly Dictionary<int, Godot.Mesh> _placeholders = new();

    /// <summary>
    /// Chunk -> entity id -> what was drawn for it, so one prop can be removed on its own.
    /// </summary>
    /// <remarks>
    /// A drawing ledger, not a second copy of the world. It answers only "what did I draw for this id"; what
    /// actually stands in a column is <c>ChunkStreamManager</c>'s retained batch, which is pruned in step with
    /// the manifest and is the replay source when a renderer attaches late. Keeping entries here instead would
    /// mean a removal had to be applied to both, or a re-attach would redraw a crystal already collected.
    ///
    /// <para>
    /// Holds <i>every</i> prop, not only the collectible ones. It used to hold only those, on the reading that
    /// nothing else could be removed individually - which was never quite true, since a felled tree is removed
    /// by the same <c>StaticEntityRemovedSMSG</c> and simply found no entry here to act on.
    /// </para>
    /// </remarks>
    private readonly Dictionary<ChunkKey, Dictionary<long, Drawn>> _drawn = new();

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

    private static readonly StringName BladeTip = "color";
    private static readonly StringName BladeBase = "color2";
    private static readonly StringName UvVAtTip = "uv_v_at_tip";

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

      // Only allocated once this chunk turns out to hold something that is not a tree, which most do.
      Dictionary<int, List<ChunkStaticEntitiesSMSG.Entry>> batched = null;

      foreach (var entry in batch.Entries)
      {
        var appearance = PropAppearance.Of(entry.Kind);

        if (appearance.HasScene)
        {
          AddSceneProp(container, batch.Key, entry, appearance);
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
        AddBatch(container, batch.Key, kind, entries);
      }
    }

    /// <summary>
    /// Instantiates one visual scene for a prop that has one.
    /// </summary>
    /// <remarks>
    /// Scaled <b>uniformly</b>, as the batched art is and unlike a placeholder box's y-only scaling. A
    /// placeholder is a unit box whose width means nothing, so stretching it vertically is the only sensible
    /// reading of a height; a real model is proportioned, and the generator draws a tree's trunk height and
    /// crown radius off the same roll specifically so that a big tree has a big crown. Scaling one axis would
    /// give a tall tree a sapling's canopy.
    /// </remarks>
    private void AddSceneProp(
      Node3D container, ChunkKey key, ChunkStaticEntitiesSMSG.Entry entry, PropAppearance.Kind appearance)
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

      Record(key, entry.EntityId, new Drawn { Node = node });
    }

    /// <summary>
    /// Draws every prop of one kind in this chunk as a single multimesh, plus a click target each if the kind
    /// is collectible.
    /// </summary>
    /// <remarks>
    /// One method for both the real art and the placeholder boxes, because the difference between them is two
    /// lines: which mesh goes in, and whether the transform is scaled proportionally or only vertically.
    ///
    /// <para>
    /// <b>Art scales uniformly</b>, for <see cref="AddSceneProp"/>'s reason - a plant that grew tall grew wide
    /// - and because the wind in <c>grass.gdshader</c> inverts this basis on the assumption that it is a yaw
    /// and a uniform scale, which makes the inverse a transpose rather than a general one.
    /// </para>
    ///
    /// <para>
    /// <b>A placeholder scales on y only</b>: the mesh is a box of the kind's own width standing on its own
    /// origin, so this makes a tall thing tall rather than also fat. A building is the exception and is scaled
    /// on all three axes, because its footprint arrives per entry - see
    /// <see cref="ChunkStaticEntitiesSMSG.Entry.HasFootprint"/>. Its x and z factors are <i>relative to the
    /// kind's own placeholder width</i>, since the shared mesh is already that wide; a building whose row said
    /// 5 m and whose lot is 9 m across is scaled by 1.8 rather than by 9. Keeping the shared mesh is what lets
    /// a whole town's houses stay one multimesh instead of one draw each.
    /// </para>
    /// </remarks>
    private void AddBatch(
      Node3D container, ChunkKey key, int kind, List<ChunkStaticEntitiesSMSG.Entry> entries)
    {
      var appearance = PropAppearance.Of(kind);

      // Null when the kind has no art, and also when it named art that would not load - in which case it falls
      // back to the placeholder box rather than to nothing, on the reasoning behind PropAppearance's magenta:
      // a prop that is drawn wrong is a bug report, and one that is not drawn at all is indistinguishable from
      // ground that genuinely has nothing on it.
      var art = appearance.HasMesh ? ArtFor(kind, appearance) : null;

      var multi = new MultiMesh
      {
        TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
        Mesh = art ?? PlaceholderFor(kind),
        InstanceCount = entries.Count
      };

      // The mesh's own footprint, which is what a pick box is sized against once a kind is drawn as art. Zero
      // for a placeholder, where PlaceholderWidth is the answer instead.
      var footprint = 0f;
      if (art != null)
      {
        var box = art.GetAabb().Size;
        footprint = Mathf.Max(box.X, box.Z);
      }

      // The width the shared placeholder mesh was built at, which every per-entry footprint is expressed
      // against. Guarded because a row with a zero width would otherwise divide by it.
      var meshWidth = Mathf.Max(appearance.PlaceholderWidth, 0.001f);

      for (var i = 0; i < entries.Count; i++)
      {
        var entry = entries[i];
        var position = GroundedPositionOf(entry);

        Basis basis;
        float pickWidth;

        if (art != null)
        {
          var scale = appearance.NaturalHeight > 0f ? entry.Height / appearance.NaturalHeight : 1f;
          basis = new Basis(Vector3.Up, entry.Yaw).Scaled(new Vector3(scale, scale, scale));
          pickWidth = footprint * scale;
        }
        else
        {
          // Local x is the facing axis, so half-length scales x and half-width scales z. Doubled because the
          // wire carries half-extents and the mesh is a full width.
          var along = entry.HasFootprint ? entry.HalfLength * 2f / meshWidth : 1f;
          var across = entry.HasFootprint ? entry.HalfWidth * 2f / meshWidth : 1f;

          // The server's z is the ground the prop stands on, and a mesh built upward from its own origin wants
          // that as its base.
          basis = new Basis(Vector3.Up, entry.Yaw).Scaled(new Vector3(along, entry.Height, across));
          pickWidth = appearance.PlaceholderWidth;
        }

        multi.SetInstanceTransform(i, new Transform3D(basis, position));

        var picker = appearance.Collectible
          ? AddPicker(container, entry, position, pickWidth)
          : null;

        Record(key, entry.EntityId, new Drawn { Node = picker, Batch = multi, Slot = i });
      }

      var node = new MultiMeshInstance3D { Multimesh = multi };

      if (art != null)
      {
        var material = MaterialFor(kind, appearance);
        if (material != null)
        {
          node.MaterialOverride = material;
        }

        // The wind in grass.gdshader moves vertices that Godot's own bounds know nothing about, so a leaning
        // tuft at the edge of the frustum would be culled with its blades still on screen. A flat margin
        // rather than a computed AABB because the displacement has a hard ceiling: `wind_max_lean` times the
        // tallest thing drawn this way, which is a reed at about 2.3 m.
        node.ExtraCullMargin = WindReach;
      }

      container.AddChild(node);
    }

    /// <summary>
    /// Gives one prop a click target: an <see cref="Area3D"/> standing where it does, carrying its id.
    /// </summary>
    /// <remarks>
    /// The pick box is at least <see cref="MinPickWidth"/> across, because a 0.3 m shard at any distance is
    /// otherwise a target nobody can hit. It is deliberately not the server's <c>collider</c> block from
    /// <c>prop-kinds.yml</c>: that one is for movement, which wants the real extent, and this one is for
    /// clicking, which wants a generous one.
    ///
    /// <para>
    /// For a kind with art the width is the <i>drawn</i> footprint, which for a clump of grass is a couple of
    /// metres and far wider than the row's <c>PlaceholderWidth</c>. That is the point: what the player is
    /// aiming at is the blades they can see, and a 0.6 m box in the middle of a 2.3 m clump would refuse
    /// clicks that visibly landed on the plant.
    /// </para>
    ///
    /// <para>
    /// No yaw on it, unlike the mesh instance: the box is square in x and z, so turning it changes nothing.
    /// </para>
    /// </remarks>
    private Node3D AddPicker(
      Node3D container, ChunkStaticEntitiesSMSG.Entry entry, Vector3 position, float width)
    {
      var area = new Area3D
      {
        Name = $"prop {entry.EntityId}",
        Transform = new Transform3D(Basis.Identity, position)
      };

      // Before AddChild, so the script is attached by the time the node enters the tree - the same ordering
      // TerrainRenderer.NewBody documents.
      area.SetScript(PropPicker);

      var size = Mathf.Max(width, MinPickWidth);
      area.AddChild(new CollisionShape3D
      {
        Shape = new BoxShape3D { Size = new Vector3(size, entry.Height, size) },
        // The prop grows upward from its own origin, so the box's centre sits at half its height.
        Position = new Vector3(0f, entry.Height * 0.5f, 0f)
      });

      area.Set("entity_id", entry.EntityId);
      area.Set("kind", entry.Kind);

      // Physics picking is a signal on the collision object, and a node built in code has to wire it itself.
      area.Connect(CollisionObject3D.SignalName.InputEvent, new Callable(area, "_on_input_event"));

      container.AddChild(area);

      return area;
    }

    /// <summary>Notes what was drawn for one prop, so <see cref="RemoveEntity"/> can find it again.</summary>
    private void Record(ChunkKey key, long entityId, Drawn drawn)
    {
      if (!_drawn.TryGetValue(key, out var byId))
      {
        byId = new Dictionary<long, Drawn>();
        _drawn[key] = byId;
      }

      byId[entityId] = drawn;
    }

    /// <summary>
    /// Un-draws one prop, leaving the rest of its chunk alone.
    /// </summary>
    /// <remarks>
    /// Surgical rather than re-running <see cref="Apply"/> for the chunk, because <see cref="Apply"/> replaces
    /// the whole container: collecting one herb would free and re-instantiate every <c>TreeVisual</c> in the
    /// column, which is a visible hitch for a change to a single prop.
    ///
    /// <para>
    /// Idempotent, which matters more than it looks. A prop that was promoted before being collected carries a
    /// <c>Dirtyable</c> <c>Health</c>, so its destruction also emits the ordinary entity vanish alongside our
    /// map-channel removal - and a chunk that unloads between the two calls has already taken the node.
    /// </para>
    /// </remarks>
    /// <returns>true if something was actually taken away</returns>
    public bool RemoveEntity(ChunkKey key, long entityId)
    {
      if (!_drawn.TryGetValue(key, out var byId) || !byId.Remove(entityId, out var drawn))
      {
        return false;
      }

      if (byId.Count == 0)
      {
        _drawn.Remove(key);
      }

      // A MultiMesh can only truncate its tail, so an arbitrary instance goes away by being parked at a zero
      // basis: every triangle collapses to a point and the rasteriser drops it. The slot is not reused - the
      // next batch for this chunk rebuilds the whole buffer anyway.
      if (drawn.Batch != null && IsInstanceValid(drawn.Batch))
      {
        drawn.Batch.SetInstanceTransform(
          drawn.Slot, new Transform3D(new Basis(Vector3.Zero, Vector3.Zero, Vector3.Zero), Vector3.Zero));
      }

      if (drawn.Node != null && IsInstanceValid(drawn.Node))
      {
        drawn.Node.QueueFree();
      }

      return true;
    }

    /// <summary>Drops everything drawn for a chunk. Safe to call for a chunk that was never drawn.</summary>
    public void Remove(ChunkKey key)
    {
      // Dropped wholesale rather than per prop: the container's own QueueFree below already takes the scene
      // instances, the multimeshes and the pickers with it, so this is only the ledger catching up.
      _drawn.Remove(key);

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

    /// <summary>The batched art mesh for a kind, loaded once, or null if it could not be loaded.</summary>
    /// <remarks>
    /// Cached by kind rather than by path even though six kinds name two meshes between them, because
    /// <see cref="ResourceLoader"/> already returns the same instance for the same path - so the second kind
    /// to ask costs a dictionary miss and not a second copy of the mesh.
    /// </remarks>
    private Godot.Mesh ArtFor(int kind, PropAppearance.Kind appearance)
    {
      if (_art.TryGetValue(kind, out var cached))
      {
        return cached;
      }

      var mesh = ResourceLoader.Load<Godot.Mesh>(appearance.MeshPath);
      if (mesh == null)
      {
        GD.PushError(
          $"[props] kind {kind} names {appearance.MeshPath}, which did not load; it falls back to a box.");
      }

      _art[kind] = mesh;
      return mesh;
    }

    /// <summary>
    /// The material a kind's art is drawn with, built once, or null to keep the mesh's own.
    /// </summary>
    /// <remarks>
    /// Duplicated per kind rather than shared, so that a blighted twin can differ from its healthy one by two
    /// colours without a second material file. A shallow duplicate: the noise texture behind the albedo is a
    /// sub-resource and stays shared between all six.
    /// </remarks>
    private Material MaterialFor(int kind, PropAppearance.Kind appearance)
    {
      if (_materials.TryGetValue(kind, out var cached))
      {
        return cached;
      }

      Material material = null;

      if (!string.IsNullOrEmpty(appearance.MaterialPath))
      {
        var template = ResourceLoader.Load<ShaderMaterial>(appearance.MaterialPath);

        if (template == null)
        {
          GD.PushError(
            $"[props] kind {kind} names {appearance.MaterialPath}, which did not load; " +
            "it falls back to the mesh's own material.");
        }
        else
        {
          var own = (ShaderMaterial)template.Duplicate();
          own.SetShaderParameter(UvVAtTip, appearance.UvVAtTip);

          if (appearance.BladeTip.HasValue)
          {
            own.SetShaderParameter(BladeTip, appearance.BladeTip.Value);
          }

          if (appearance.BladeBase.HasValue)
          {
            own.SetShaderParameter(BladeBase, appearance.BladeBase.Value);
          }

          material = own;
        }
      }

      _materials[kind] = material;
      return material;
    }

    /// <summary>A placeholder mesh for a kind with no art, built once.</summary>
    /// <remarks>
    /// Unit height, because <see cref="AddBatch"/> scales a placeholder by the prop's own height.
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

    /// <summary>
    /// How far, in metres, the wind can carry a blade outside the bounds Godot culls its batch against.
    /// </summary>
    /// <remarks>
    /// <c>grass.gdshader</c>'s <c>wind_max_lean</c> is 0.45 of a plant's height at the tip, and the tallest
    /// thing drawn this way is a reed at the top of its spread, about 2.3 m. Rounded up, and generous on
    /// purpose: the cost of an over-large margin is a batch drawn a frame after it could have been culled,
    /// and the cost of a short one is grass vanishing at the edge of the screen in a gale.
    /// </remarks>
    private const float WindReach = 1.2f;
  }
}
