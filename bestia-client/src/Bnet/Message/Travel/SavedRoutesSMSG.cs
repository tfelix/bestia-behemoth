using Godot;

namespace BestiaBehemothClient.Bnet.Message.Travel
{
  /// <summary>
  /// Every route the selected master keeps, sent on request and whenever one changes.
  /// </summary>
  [GlobalClass]
  public partial class SavedRoutesSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<SavedRouteInfo> Routes { get; set; } = new Godot.Collections.Array<SavedRouteInfo>();

    public static SavedRoutesSMSG FromProto(global::Bnet.SavedRoutesSMSG proto)
    {
      var message = new SavedRoutesSMSG();
      foreach (var route in proto.Routes)
      {
        message.Routes.Add(SavedRouteInfo.FromProto(route));
      }

      return message;
    }
  }
}
