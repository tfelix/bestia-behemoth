using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class EquipItemCMSG : ICMSG
  {
    public ulong ItemId { get; set; }

    /// <summary>
    /// Id of the backing item instance, or 0 when the client does not know it yet.
    /// </summary>
    public ulong UniqueId { get; set; }

    /// <summary>
    /// EquipmentSlot ordinal - must match Game/Item/equipment_slot.gd (and the server enum).
    /// </summary>
    public uint Slot { get; set; }

    public override Envelope ToEnvelope()
    {
      var equipItem = new global::Bnet.EquipItemCMSG
      {
        ItemId = ItemId,
        UniqueId = UniqueId,
        Slot = Slot
      };

      return new Envelope
      {
        EquipItem = equipItem
      };
    }
  }
}
