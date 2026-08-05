using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Draws the static entities of each held chunk: trees, mana crystals, wound spires.
  /// </summary>
  /// <remarks>
  /// A sibling of <see cref="TerrainRenderer"/> and it keeps the same contract: one node per chunk, removed
  /// when the chunk leaves the manifest. That is the only lifecycle rule there is - the server sends a batch
  /// behind each chunk payload and never sends a removal for an individual entity, so a chunk going away is
  /// what takes its contents with it.
  ///
  /// <para>
  /// One <see cref="MultiMeshInstance3D"/> per (kind, chunk) rather than a node per prop. A view volume can
  /// hold thousands of trees, and thousands of <c>Node3D</c>s is a scene tree Godot spends its frame walking;
  /// a multimesh is one draw call per kind per chunk with the transforms in a buffer. This is also why props
  /// deliberately do not go through <c>EntityManager</c>, which instantiates a full <c>Entity.tscn</c> - health
  /// bar, nameplate, chat bubble - per entity.
  /// </para>
  ///
  /// <para>
  /// <b>Unbuilt and unrun.</b> The meshes are placeholder boxes and cylinders built in code; there is no art
  /// yet and no <c>PropDB</c> resource table to look one up from. What this file establishes is the plumbing
  /// and the lifecycle, so that dropping real meshes in later is a change to <see cref="MeshFor"/> and nothing
  /// else.
  /// </para>
  /// </remarks>
  public partial class StaticEntityRenderer : Node3D
  {
    /// <summary>Chunk -> the multimesh nodes drawing it, one per kind present.</summary>
    private readonly Dictionary<ChunkKey, List<MultiMeshInstance3D>> _byChunk = new();

    /// <summary>One mesh per kind, built once. Placeholders until there is art.</summary>
    private readonly Dictionary<int, Mesh> _meshes = new();

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

      // Grouped by kind, because a multimesh holds one mesh.
      var byKind = new Dictionary<int, List<ChunkStaticEntitiesSMSG.Entry>>();
      foreach (var entry in batch.Entries)
      {
        if (!byKind.TryGetValue(entry.Kind, out var list))
        {
          list = new List<ChunkStaticEntitiesSMSG.Entry>();
          byKind[entry.Kind] = list;
        }
        list.Add(entry);
      }

      var nodes = new List<MultiMeshInstance3D>(byKind.Count);

      foreach (var (kind, entries) in byKind)
      {
        var multi = new MultiMesh
        {
          TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
          Mesh = MeshFor(kind),
          InstanceCount = entries.Count
        };

        for (var i = 0; i < entries.Count; i++)
        {
          var entry = entries[i];

          // The server's z is the ground the prop stands on, and a mesh built upward from its own origin wants
          // that as its base. Scaled on y only: a tall tree is a tall tree, not a fat one.
          var basis = new Basis(Vector3.Up, entry.Yaw).Scaled(new Vector3(1f, entry.Height, 1f));
          multi.SetInstanceTransform(i, new Transform3D(basis, entry.Position));
        }

        var node = new MultiMeshInstance3D { Multimesh = multi };
        AddChild(node);
        nodes.Add(node);
      }

      _byChunk[batch.Key] = nodes;
    }

    /// <summary>Drops everything drawn for a chunk. Safe to call for a chunk that was never drawn.</summary>
    public void Remove(ChunkKey key)
    {
      if (!_byChunk.Remove(key, out var nodes))
      {
        return;
      }

      foreach (var node in nodes)
      {
        node.QueueFree();
      }
    }

    public void Clear()
    {
      foreach (var key in new List<ChunkKey>(_byChunk.Keys))
      {
        Remove(key);
      }
    }

    /// <summary>
    /// A placeholder mesh per kind.
    /// </summary>
    /// <remarks>
    /// Deliberately crude and deliberately *different* per kind, so that a wrong-kind bug is visible rather
    /// than merely wrong: a tree is a tall box, a crystal a narrow one, a spire a tall thin one. Unit height,
    /// because <see cref="Apply"/> scales by the prop's own height.
    /// </remarks>
    private Mesh MeshFor(int kind)
    {
      if (_meshes.TryGetValue(kind, out var cached))
      {
        return cached;
      }

      // Kind ordinals mirror the server's StaticEntityKind: TREE, BLIGHTED_TREE, MANA_CRYSTAL_SMALL,
      // MANA_CRYSTAL_LARGE, WOUND_SPIRE, AETHERITE_SHARD_SMALL, AETHERITE_SHARD_LARGE.
      var (width, colour) = kind switch
      {
        0 => (0.6f, new Color(0.20f, 0.45f, 0.15f)),
        1 => (0.6f, new Color(0.30f, 0.26f, 0.20f)),
        2 => (0.3f, new Color(0.35f, 0.55f, 0.85f)),
        3 => (0.5f, new Color(0.45f, 0.35f, 0.85f)),
        4 => (0.4f, new Color(0.75f, 0.20f, 0.70f)),
        // The shards, in the aetherite ore's violet rather than the mana crystals' blue - a player who has
        // learned what the ore blocks look like underground should recognise what is lying on the grass above
        // them, because recognising it is the entire point of the prop. Squat and wide, unlike a crystal.
        5 => (0.7f, new Color(0.42f, 0.33f, 0.52f)),
        6 => (0.9f, new Color(0.58f, 0.40f, 0.78f)),
        _ => (0.5f, new Color(1f, 0f, 1f))
      };

      var mesh = new BoxMesh
      {
        Size = new Vector3(width, 1f, width),
        Material = new StandardMaterial3D { AlbedoColor = colour }
      };

      _meshes[kind] = mesh;
      return mesh;
    }
  }
}
