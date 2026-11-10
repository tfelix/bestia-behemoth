using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>One line of a station's queue. Only the first carries progress.</summary>
  [GlobalClass]
  public partial class StationQueueEntry : GodotObject
  {
    [Export] public ulong RecipeId { get; set; }
    [Export] public uint Amount { get; set; }
    [Export] public uint Done { get; set; }
    [Export] public uint ProgressMillis { get; set; }
    [Export] public uint CraftMillis { get; set; }
  }
}
