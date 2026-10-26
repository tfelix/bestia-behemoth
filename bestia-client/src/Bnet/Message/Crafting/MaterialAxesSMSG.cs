using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Where every crafting material sits in the feature space, sent on connect. Each value list holds one number
  /// per axis, -1..1, in the order of <c>craft_axes.gd</c>.
  /// </summary>
  [GlobalClass]
  public partial class MaterialAxesSMSG : ISMSG
  {
    [Export] public Godot.Collections.Dictionary<long, float[]> AxesByItemId { get; set; } = new();

    public static MaterialAxesSMSG FromProto(global::Bnet.MaterialAxesSMSG proto)
    {
      var message = new MaterialAxesSMSG();
      foreach (var material in proto.Materials)
      {
        var axes = new float[material.Axes.Count];
        material.Axes.CopyTo(axes, 0);
        message.AxesByItemId[material.ItemId] = axes;
      }

      return message;
    }

    public override string ToString() => $"MaterialAxesSMSG(Count={AxesByItemId.Count})";
  }
}
