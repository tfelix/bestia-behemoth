using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Every parcel to the played master, soonest first, as seen from one post office.</summary>
  [GlobalClass]
  public partial class ParcelsSMSG : ISMSG
  {
    [Export] public ulong PostmasterEntityId { get; set; }
    [Export] public Godot.Collections.Array<ParcelEntry> Parcels { get; set; } = [];

    public static ParcelsSMSG FromProto(global::Bnet.ParcelsSMSG proto)
    {
      var parcels = new Godot.Collections.Array<ParcelEntry>();
      foreach (var parcel in proto.Parcels)
      {
        parcels.Add(ParcelEntry.FromProto(parcel));
      }

      return new ParcelsSMSG { PostmasterEntityId = proto.PostmasterEntityId, Parcels = parcels };
    }
  }
}
