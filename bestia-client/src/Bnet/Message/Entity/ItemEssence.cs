using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// A Bestia's essence bound into a gem or a socket. <see cref="Tier"/> is the server's GemTier name, which
  /// <c>EssenceText</c> keys its numbers by.
  /// </summary>
  [GlobalClass]
  public partial class ItemEssence : RefCounted
  {
    [Export] public ulong BestiaId { get; set; }
    [Export] public string Tier { get; set; } = "";

    /// <summary>Null for an unset proto field, such as the held essence of anything but an essence gem.</summary>
    public static ItemEssence FromProto(global::Bnet.ItemEssence proto)
    {
      return proto == null ? null : new ItemEssence { BestiaId = proto.BestiaId, Tier = proto.Tier };
    }

    public static Godot.Collections.Array<ItemEssence> FromProto(
      Google.Protobuf.Collections.RepeatedField<global::Bnet.ItemEssence> protoEssences)
    {
      var essences = new Godot.Collections.Array<ItemEssence>();
      foreach (var protoEssence in protoEssences)
      {
        essences.Add(FromProto(protoEssence));
      }

      return essences;
    }
  }
}
