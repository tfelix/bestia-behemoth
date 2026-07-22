using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class UnequipItemCMSG : ICMSG
  {
    /// <summary>
    /// EquipmentSlot ordinal - must match Game/Item/equipment_slot.gd (and the server enum).
    /// </summary>
    public uint Slot { get; set; }

    public override Envelope ToEnvelope()
    {
      var unequipItem = new global::Bnet.UnequipItemCMSG
      {
        Slot = Slot
      };

      return new Envelope
      {
        UnequipItem = unequipItem
      };
    }
  }
}
