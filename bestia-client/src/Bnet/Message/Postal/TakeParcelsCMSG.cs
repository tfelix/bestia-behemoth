using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Takes parcels for one office from the route board into a sealed mail sack.</summary>
  public partial class TakeParcelsCMSG : ICMSG
  {
    public ulong PostmasterEntityId { get; set; }
    public Godot.Collections.Array<long> ParcelIds { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      var take = new global::Bnet.TakeParcelsCMSG { PostmasterEntityId = PostmasterEntityId };
      foreach (var parcelId in ParcelIds)
      {
        take.ParcelIds.Add((ulong)parcelId);
      }

      return new Envelope { TakeParcels = take };
    }
  }
}
