using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>Take an entry off a station's queue; its unused ingredients come back as far as they fit.</summary>
  public partial class CancelQueueEntryCMSG : ICMSG
  {
    public ulong StructureId { get; set; }

    /// <summary>Position in <see cref="StationStateSMSG.Entries"/>.</summary>
    public uint Index { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CancelQueueEntry = new global::Bnet.CancelQueueEntryCMSG { StructureId = StructureId, Index = Index }
      };
    }
  }
}
