using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class EquipItemCMSG : ICMSG
  {
    /// <summary>The held item, as InventoryItem.PlayerItemId names it.</summary>
    public ulong PlayerItemId { get; set; }

    /// <summary>
    /// EquipmentSlot ordinal - must match Game/Item/equipment_slot.gd (and the server enum).
    /// </summary>
    public uint Slot { get; set; }

    public override Envelope ToEnvelope()
    {
      var equipItem = new global::Bnet.EquipItemCMSG
      {
        PlayerItemId = PlayerItemId,
        Slot = Slot
      };

      return new Envelope
      {
        EquipItem = equipItem
      };
    }
  }
}
