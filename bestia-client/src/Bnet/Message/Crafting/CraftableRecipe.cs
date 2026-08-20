using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// What a crafting effect does when it succeeds. Ordinals match
  /// <c>net.bestia.zone.crafting.RecipeEffect</c>, whose declaration order is the contract.
  ///
  /// Anything but <see cref="Produce"/> needs the player to pick a held item to work on.
  /// </summary>
  public enum RecipeEffect
  {
    Produce = 0,
    AddSlot = 1,
    Upgrade = 2,
    Repair = 3
  }

  /// <summary>
  /// One line of the crafting window.
  /// </summary>
  /// <remarks>
  /// Its own file rather than a companion inside <see cref="CraftableRecipesSMSG"/>, because Godot registers
  /// exactly one <c>[GlobalClass]</c> per C# file - the one named after the file. A second class in the same
  /// file compiles and is invisible to GDScript, which is why <c>SkillListEntry</c> and <c>DialogArg</c> each
  /// have a file of their own too.
  /// </remarks>
  [GlobalClass]
  public partial class CraftableRecipe : GodotObject
  {
    [Export] public ulong RecipeId { get; set; }

    [Export] public RecipeEffect Effect { get; set; } = RecipeEffect.Produce;

    /// <summary>
    /// <see cref="Effect"/> as a lowercase snake_case string, for the GDScript side - it cannot see a C#
    /// enum's members, so matching on this avoids re-declaring the ordinals over there by hand. The same
    /// reasoning <see cref="EnumName"/> spells out.
    /// </summary>
    [Export] public string EffectName { get; set; } = "produce";

    /// <summary>Both 0 for an effect that changes an item rather than making one.</summary>
    [Export] public uint OutputItemId { get; set; }

    [Export] public uint OutputAmount { get; set; }

    [Export] public Godot.Collections.Array<RecipeInput> Inputs { get; set; } = [];

    /// <summary>Per mille, so no float crosses the wire for a number shown as a percentage.</summary>
    [Export] public uint SuccessPermille { get; set; }

    /// <summary>After the crafter's construction-time reduction - the wait they will actually see.</summary>
    [Export] public uint CraftMillis { get; set; }

    /// <summary>
    /// Whether every input is held right now. A recipe is still listed when it is not: a recipe you
    /// cannot afford is what tells you what to go and gather.
    /// </summary>
    [Export] public bool InputsHeld { get; set; }

    /// <summary>True when the player has to pick a held item for this recipe to act on.</summary>
    [Export] public bool NeedsTarget { get; set; }

    public static CraftableRecipe FromProto(global::Bnet.CraftableRecipe proto)
    {
      var recipe = new CraftableRecipe
      {
        RecipeId = proto.RecipeId,
        OutputItemId = proto.OutputItemId,
        OutputAmount = proto.OutputAmount,
        SuccessPermille = proto.SuccessPermille,
        CraftMillis = proto.CraftMillis,
        InputsHeld = proto.InputsHeld
      };

      recipe.SetEffect((RecipeEffect)proto.Effect);

      foreach (var input in proto.Inputs)
      {
        recipe.Inputs.Add(RecipeInput.FromProto(input));
      }

      return recipe;
    }

    private void SetEffect(RecipeEffect effect)
    {
      Effect = effect;
      EffectName = EnumName.Of(effect);
      NeedsTarget = effect != RecipeEffect.Produce;
    }
  }
}
