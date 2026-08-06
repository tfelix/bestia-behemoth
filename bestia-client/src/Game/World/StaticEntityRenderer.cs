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
  /// Nothing here reads <c>Entry.EntityId</c> yet. Props are drawn but not clickable: a multimesh instance is
  /// not pickable at all, and the scene instances carry no collision shape, so there is no path from a click
  /// to the entity id the server would need to resolve a target. The whole server side of that
  /// (<c>PropPromotionService</c>, <c>PropDeathDivergenceSystem</c>, loot, regrowth) already exists and is
  /// waiting on it.
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
    /// Metres per voxel, so that a prop stands where the ground it was grounded against was drawn.
    /// </summary>
    /// <remarks>
    /// The wire carries voxel coordinates and <see cref="TerrainRenderer"/> scales them by this to reach
    /// Godot units, so anything placed on that terrain has to apply the same factor. Heights and radii do
    /// not: they arrive in metres already, and a Godot unit is a metre.
    /// </remarks>
    private float _voxelSize = 1.0f;

    /// <summary>
    /// Adopts the world's units. Safe to call with null, which keeps the defaults.
    /// </summary>
    /// <remarks>
    /// Separate from the constructor for the reason <see cref="TerrainRenderer.Configure"/> is: the server
    /// sends <c>WorldInfoSMSG</c> the instant a connection authenticates, which is during master selection,
    /// and the Game scene that owns this renderer does not exist until a master has been chosen. So this is
    /// always called after that message has come and gone, by <c>ChunkStreamManager</c>'s own setter.
    /// </remarks>
    public void Configure(WorldInfoSMSG worldInfo)
    {
      if (worldInfo != null)
      {
        _voxelSize = (float)worldInfo.VoxelSizeMetres;
      }

      Clear();
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

      node.Transform = new Transform3D(basis, (Vector3)entry.Position * _voxelSize);
      container.AddChild(node);
    }

    /// <summary>
    /// Draws every prop of one artless kind in this chunk as a single multimesh.
    /// </summary>
    /// <remarks>
    /// Scaled on y only: the mesh is a unit box standing on its own origin, so this makes a tall thing tall
    /// rather than also fat. See <see cref="AddSceneProp"/> for why a real model is treated differently.
    /// </remarks>
    private void AddPlaceholderBatch(Node3D container, int kind, List<ChunkStaticEntitiesSMSG.Entry> entries)
    {
      var multi = new MultiMesh
      {
        TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
        Mesh = PlaceholderFor(kind),
        InstanceCount = entries.Count
      };

      for (var i = 0; i < entries.Count; i++)
      {
        var entry = entries[i];

        // The server's z is the ground the prop stands on, and a mesh built upward from its own origin wants
        // that as its base.
        var basis = new Basis(Vector3.Up, entry.Yaw).Scaled(new Vector3(1f, entry.Height, 1f));
        multi.SetInstanceTransform(i, new Transform3D(basis, (Vector3)entry.Position * _voxelSize));
      }

      container.AddChild(new MultiMeshInstance3D { Multimesh = multi });
    }

    /// <summary>Drops everything drawn for a chunk. Safe to call for a chunk that was never drawn.</summary>
    public void Remove(ChunkKey key)
    {
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
  }
}
