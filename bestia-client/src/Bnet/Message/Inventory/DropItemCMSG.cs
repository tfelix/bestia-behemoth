using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class DropItemCMSG : ICMSG
  {
    public ulong ItemId { get; set; }
    public uint Amount { get; set; }

    /// Which copy to drop; 0 for a plain stack. See the field comment in drop_item_cmsg.proto.
    public ulong UniqueId { get; set; }

    public override Envelope ToEnvelope()
    {
      var dropItem = new global::Bnet.DropItemCMSG
      {
        ItemId = ItemId,
        Amount = Amount,
        UniqueId = UniqueId
      };

      return new Envelope
      {
        DropItem = dropItem
      };
    }
  }
}
