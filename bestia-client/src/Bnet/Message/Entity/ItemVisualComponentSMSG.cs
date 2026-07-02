using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for updating an entity's ground item visual component.
  /// Contains the entity ID and the dropped item's id, amount and unique id.
  /// </summary>
  [GlobalClass]
  public partial class ItemVisualComponentSMSG : EntitySMSG
  {
    [Export]
    public uint ItemId { get; set; } = 0;

    [Export]
    public uint Amount { get; set; } = 0;

    [Export]
    public ulong UniqueId { get; set; } = 0;

    public static ItemVisualComponentSMSG FromProto(global::Bnet.ItemVisualComponent protoItemVisual)
    {
      return new ItemVisualComponentSMSG
      {
        EntityId = protoItemVisual.EntityId,
        ItemId = protoItemVisual.ItemId,
        Amount = protoItemVisual.Amount,
        UniqueId = protoItemVisual.UniqueId
      };
    }
  }
}
