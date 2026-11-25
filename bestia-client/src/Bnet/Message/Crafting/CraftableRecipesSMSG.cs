using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// What the player can make with the skill they just activated, at the station they are standing next
  /// to. Account-scoped rather than entity-scoped, like <c>DialogSMSG</c>: this opens a window, and a
  /// window belongs to a client rather than to an entity in the world.
  ///
  /// Carries no names. A producing recipe is named by its output item, which the client resolves through
  /// its own item DB; the three item-changing effects are named by the effect. That is what spares the
  /// recipe catalogue a second localized copy over here.
  /// </summary>
  [GlobalClass]
  public partial class CraftableRecipesSMSG : ISMSG
  {
    /// <summary>The skill the window shows first, or 0 when none was picked.</summary>
    [Export] public ulong SkillId { get; set; }

    /// <summary>
    /// The station the server found in range, as a <c>StaticEntityKind</c> ordinal + 1; 0 means none was
    /// needed or none was there. Presentation only.
    /// </summary>
    [Export] public uint StationKind { get; set; }

    /// <summary>
    /// <see cref="StationKind"/> as something to put in a window title, or empty for work needing no station.
    ///
    /// Resolved here rather than in GDScript so the <c>StaticEntityKind</c> ordinals stay in one place on this
    /// side - the same reason <see cref="Game.World.PropAppearance"/> keeps the mesh table in C#. English for
    /// now, like the rest of the crafting window: none of it has a localization row yet.
    /// </summary>
    [Export] public string StationName { get; set; } = string.Empty;

    /// <summary>The player structure the recipes are worked at, or 0 for Handiwork.</summary>
    [Export] public ulong StructureId { get; set; }

    /// <summary>Whether the station burns wood, which is what the window offers to add.</summary>
    [Export] public bool BurnsFuel { get; set; }

    /// <summary>A Carving Table, where Bestias are locked in and etched.</summary>
    [Export] public bool IsCarvingTable { get; set; }

    /// <summary>Seconds the fire has left; 0 once it is out.</summary>
    [Export] public uint FuelSeconds { get; set; }

    [Export] public Godot.Collections.Array<CraftableRecipe> Recipes { get; set; } = [];

    /// <summary>The Bestias locked into this Carving Table. Empty at every other station.</summary>
    [Export] public Godot.Collections.Array<DepositedBestia> Deposited { get; set; } = [];

    /// <summary>The player's own Bestias that could be locked into this Carving Table.</summary>
    [Export] public Godot.Collections.Array<DepositableBestia> Depositable { get; set; } = [];

    /// <summary>The station's entity, for the window's buttons to click; 0 when it was not opened by clicking it.</summary>
    [Export] public ulong StationEntityId { get; set; }

    public static CraftableRecipesSMSG FromProto(global::Bnet.CraftableRecipesSMSG proto)
    {
      var message = new CraftableRecipesSMSG
      {
        SkillId = proto.SkillId,
        StationKind = proto.StationKind,
        StationName = NameOfStation(proto.StationKind),
        StructureId = proto.StructureId,
        FuelSeconds = proto.FuelSeconds,
        StationEntityId = proto.StationEntityId,
        BurnsFuel = proto.StationKind == CampfireKind,
        IsCarvingTable = proto.StationKind == CarvingTableKind
      };

      foreach (var recipe in proto.Recipes)
      {
        message.Recipes.Add(CraftableRecipe.FromProto(recipe));
      }

      foreach (var deposited in proto.Deposited)
      {
        message.Deposited.Add(DepositedBestia.FromProto(deposited));
      }

      foreach (var depositable in proto.Depositable)
      {
        message.Depositable.Add(DepositableBestia.FromProto(depositable));
      }

      return message;
    }

    public override string ToString()
    {
      return $"CraftableRecipesSMSG(SkillId={SkillId}, Station={StationKind}, Count={Recipes.Count})";
    }

    /// <summary>
    /// The station kinds a craft can happen at, keyed by <c>StaticEntityKind</c> ordinal <b>+ 1</b> - 0 means
    /// none, which is why the encoding is offset at all.
    /// </summary>
    private static readonly global::System.Collections.Generic.Dictionary<uint, string> StationNames = new()
    {
      { 23, "Workbench" },
      { 24, "Furnace" },
      { 25, "Forge" },
      { CampfireKind, "Campfire" },
      { 76, "Tanning Rack" },
      { 77, "Loom" },
      { 78, "Glass Furnace" },
      { 79, "Glassblowing Bench" },
      { 87, "Alchemy Laboratory" },
      { 88, "Gem Cut Station" },
      { 89, "Transmutation Workbench" },
      { CarvingTableKind, "Carving Table" },
      { 106, "Mint" }
    };

    /// <summary><c>StaticEntityKind.CAMPFIRE</c> ordinal + 1, the one station that burns fuel.</summary>
    private const uint CampfireKind = 56;
    private const uint CarvingTableKind = 94;

    private static string NameOfStation(uint stationKind)
    {
      return StationNames.TryGetValue(stationKind, out var name) ? name : string.Empty;
    }
  }
}
