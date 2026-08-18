using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// One material a recipe consumes, with how many the crafter holds so the window can show "2/3".
  /// </summary>
  /// <remarks>
  /// Its own file for the reason <see cref="CraftableRecipe"/> is in one: Godot registers a single
  /// <c>[GlobalClass]</c> per C# file, the one named after the file.
  /// </remarks>
  [GlobalClass]
  public partial class RecipeInput : GodotObject
  {
    [Export] public uint ItemId { get; set; }
    [Export] public uint Amount { get; set; }
    [Export] public uint Held { get; set; }

    public static RecipeInput FromProto(global::Bnet.RecipeInput proto)
    {
      return new RecipeInput
      {
        ItemId = proto.ItemId,
        Amount = proto.Amount,
        Held = proto.Held
      };
    }
  }
}
