using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class DropItemCMSG : ICMSG
  {
    /// <summary>The held item, as InventoryItem.PlayerItemId names it.</summary>
    public ulong PlayerItemId { get; set; }
    public uint Amount { get; set; }

    public override Envelope ToEnvelope()
    {
      var dropItem = new global::Bnet.DropItemCMSG
      {
        PlayerItemId = PlayerItemId,
        Amount = Amount
      };

      return new Envelope
      {
        DropItem = dropItem
      };
    }
  }
}
