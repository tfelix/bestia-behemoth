using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class TransferItemCMSG : ICMSG
  {
    /// <summary>The master or own bestia handing the item over.</summary>
    public ulong FromEntityId { get; set; }
    /// <summary>The master or own bestia receiving it.</summary>
    public ulong ToEntityId { get; set; }
    /// <summary>The held item in the giver's bag, as InventoryItem.PlayerItemId names it.</summary>
    public ulong PlayerItemId { get; set; }
    public uint Amount { get; set; }

    public override Envelope ToEnvelope()
    {
      var transferItem = new global::Bnet.TransferItemCMSG
      {
        FromEntityId = FromEntityId,
        ToEntityId = ToEntityId,
        PlayerItemId = PlayerItemId,
        Amount = Amount
      };

      return new Envelope
      {
        TransferItem = transferItem
      };
    }
  }
}
