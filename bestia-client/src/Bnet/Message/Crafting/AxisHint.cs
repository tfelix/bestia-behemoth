using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Which way a tried mix is off: <see cref="Axis"/> in the order of <c>craft_axes.gd</c>, and whether the mix
  /// leans too far to the positive pole ("too solid") or the negative one ("too fluid").
  /// </summary>
  [GlobalClass]
  public partial class AxisHint : GodotObject
  {
    [Export] public int Axis { get; set; }
    [Export] public bool TooHigh { get; set; }
  }
}
