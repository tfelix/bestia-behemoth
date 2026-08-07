using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// One static entity is gone from a chunk this client still holds.
  /// </summary>
  /// <remarks>
  /// The counterpart to <see cref="ChunkStaticEntitiesSMSG"/>, and a <see cref="MapSMSG"/> for the same
  /// reason: it belongs to <c>ChunkStreamManager</c>, not to <c>EntityManager</c>. Routing it as an entity
  /// message would be actively wrong - <c>entity_manager.gd</c> calls <c>_get_or_create_entity</c> before it
  /// tests the message type, so a removal for an id it never had would mint a whole <c>Entity.tscn</c>
  /// (health bar, nameplate, chat bubble, damage numbers) only to throw it away.
  ///
  /// <para>
  /// The chunk key travels with it, so applying one is a single dictionary hit rather than a scan for the id
  /// across every batch held.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class StaticEntityRemovedSMSG : MapSMSG
  {
    public ChunkKey Key { get; private init; }

    public long EntityId { get; private init; }

    public static StaticEntityRemovedSMSG FromProto(global::Bnet.StaticEntityRemovedSMSG proto)
    {
      return new StaticEntityRemovedSMSG
      {
        Key = new ChunkKey(proto.Pos.X, proto.Pos.Y, proto.Pos.Z),
        EntityId = (long)proto.EntityId
      };
    }

    public override string ToString() => $"StaticEntityRemovedSMSG({Key}, {EntityId})";
  }
}
