using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Every static entity standing in one chunk column: trees, mana crystals, wound spires.
  /// </summary>
  /// <remarks>
  /// A <see cref="MapSMSG"/> and not an entity message, which is the whole point. These *are* real server-side
  /// entities with ids and health, but a view volume holds one to seven thousand of them, and the ordinary
  /// per-component entity path costs three separate writes each - five to twenty thousand flushes for one
  /// login, against 121 for all the terrain in the same volume. Batched per chunk it is about 25 bytes an
  /// entry.
  ///
  /// <para>
  /// Being a <c>MapSMSG</c> also keeps these away from <c>EntityManager</c>, and that matters more than the
  /// bandwidth: <c>entity_manager.gd</c> calls <c>_get_or_create_entity</c> *before* it tests the message type,
  /// so an unbranched entity message silently instantiates a full <c>Entity.tscn</c> - health bar, nameplate,
  /// chat bubble, damage numbers - per tree. And <c>get_closest_entity</c> is a linear scan whose own comment
  /// says entity counts are small.
  /// </para>
  ///
  /// <para>
  /// <b>The ids here are not durable.</b> The server destroys a column's entities when the last client stops
  /// holding it and re-mints them on return, so an <see cref="Entry.EntityId"/> is valid only while this chunk
  /// is held. It is a handle for "the thing I am clicking now", which is all a click needs; it must never be
  /// persisted or carried across a re-send.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkStaticEntitiesSMSG : MapSMSG
  {
    public ChunkKey Key { get; private init; }

    public List<Entry> Entries { get; private init; } = new();

    /// <summary>One placed object, with its position already expanded to world voxels.</summary>
    public readonly struct Entry
    {
      /// <summary>Live server entity id. See the class note: valid only while this chunk is held.</summary>
      public long EntityId { get; init; }

      /// <summary>Mirrors the server's <c>StaticEntityKind</c> ordinal.</summary>
      public int Kind { get; init; }

      /// <summary>Which of a kind's interchangeable meshes, so a wood is not one tree repeated.</summary>
      public int Variant { get; init; }

      /// <summary>Global voxel coordinates. The wire carries x and y chunk-local; this is expanded.</summary>
      public Vector3I Position { get; init; }

      public float Height { get; init; }

      public float Yaw { get; init; }
    }

    public static ChunkStaticEntitiesSMSG FromProto(global::Bnet.ChunkStaticEntitiesSMSG proto)
    {
      var key = new ChunkKey(proto.Pos.X, proto.Pos.Y, proto.Pos.Z);
      var entries = new List<Entry>(proto.Entries.Count);

      foreach (var entry in proto.Entries)
      {
        entries.Add(new Entry
        {
          EntityId = (long)entry.EntityId,
          Kind = (int)entry.Kind,
          Variant = (int)entry.Variant,
          // Local horizontally, global vertically - see the proto. Expanded here so nothing downstream has to
          // remember which of the three axes is which.
          Position = new Vector3I(
            proto.Pos.X * ChunkEngine.ChunkSize + (int)entry.LocalX,
            entry.Z,
            proto.Pos.Y * ChunkEngine.ChunkSize + (int)entry.LocalY
          ),
          Height = entry.HeightDm / 10f,
          Yaw = entry.YawCentiradians / 100f
        });
      }

      return new ChunkStaticEntitiesSMSG { Key = key, Entries = entries };
    }

    /// <summary>
    /// This batch with one entry taken out, or null if it was not in it.
    /// </summary>
    /// <remarks>
    /// A new instance rather than a mutation, because a received wire object is treated as immutable
    /// everywhere else here - <c>Key</c> and <c>Entries</c> are both <c>private init</c>. Returning null for
    /// "not present" is what makes an unknown id free at the call site: no allocation, no store, no work.
    /// </remarks>
    public ChunkStaticEntitiesSMSG Without(long entityId)
    {
      var index = Entries.FindIndex(e => e.EntityId == entityId);
      if (index < 0)
      {
        return null;
      }

      var remaining = new List<Entry>(Entries);
      remaining.RemoveAt(index);

      return new ChunkStaticEntitiesSMSG { Key = Key, Entries = remaining };
    }

    public override string ToString() => $"ChunkStaticEntitiesSMSG({Key}, {Entries.Count} entries)";
  }
}
