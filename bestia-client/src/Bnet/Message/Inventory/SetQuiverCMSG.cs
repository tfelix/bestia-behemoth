using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  /// <summary>
  /// Points the active entity's quiver at these ammo items, in the order they are shot. Replaces the whole list.
  /// </summary>
  public partial class SetQuiverCMSG : ICMSG
  {
    public Godot.Collections.Array<long> ItemIds { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      var setQuiver = new global::Bnet.SetQuiverCMSG();
      foreach (var itemId in ItemIds)
      {
        setQuiver.ItemIds.Add((uint)itemId);
      }

      return new Envelope
      {
        SetQuiver = setQuiver
      };
    }
  }
}
