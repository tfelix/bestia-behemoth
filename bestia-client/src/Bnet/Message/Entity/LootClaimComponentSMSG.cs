using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Who may pick up an item lying on the ground while only some may. Ends with <see cref="Removed"/> once
  /// anybody may.
  /// </summary>
  [GlobalClass]
  public partial class LootClaimComponentSMSG : EntitySMSG
  {
    [Export] public Godot.Collections.Array<ulong> HolderAccountIds { get; set; } = [];

    [Export] public bool Removed { get; set; }

    public static LootClaimComponentSMSG FromProto(ulong entityId, global::Bnet.LootClaimComponentSMSG proto)
    {
      var message = new LootClaimComponentSMSG { EntityId = entityId, Removed = proto.Removed };
      foreach (var accountId in proto.HolderAccountIds)
      {
        message.HolderAccountIds.Add(accountId);
      }

      return message;
    }

    public override string ToString()
    {
      return $"LootClaimComponentSMSG(EntityId={EntityId}, Holders={HolderAccountIds.Count}, Removed={Removed})";
    }
  }
}
