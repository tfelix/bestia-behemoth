using Bnet;
using Godot;
using Godot.Collections;
using Wire = Bnet.SetBestiaAiConfigCMSG;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// A whole set of bestia orders as the orders window edits it. Enums are the proto ordinals of
  /// <c>SetBestiaAiConfigCMSG</c>; a missing override key or a place id of 0 means "not set".
  /// </summary>
  [GlobalClass]
  public partial class OrderSheet : RefCounted
  {
    public static readonly string[] OverrideKeys =
      ["retreat_below_health_pct", "eat_below_stamina_pct", "heal_below_health_pct", "haul_at_load_pct", "area_radius"];

    [Export] public int Job { get; set; }
    [Export] public int Courage { get; set; }
    /// <summary>0 for none.</summary>
    [Export] public int FallbackJob { get; set; }
    /// <summary>Keyed by <see cref="OverrideKeys"/>; only the numbers that are set.</summary>
    [Export] public Dictionary Overrides { get; set; } = new();
    /// <summary>Each with <c>condition</c>, <c>action</c> and <c>threshold</c>, which is -1 where the condition takes none.</summary>
    [Export] public Array<Dictionary> Rules { get; set; } = new();
    [Export] public ulong AreaPlaceId { get; set; }
    [Export] public ulong ChestPlaceId { get; set; }

    /// <summary>A copy the window can edit without changing what it was sent.</summary>
    public OrderSheet Copy()
    {
      var copy = new OrderSheet
      {
        Job = Job,
        Courage = Courage,
        FallbackJob = FallbackJob,
        Overrides = Overrides.Duplicate(),
        AreaPlaceId = AreaPlaceId,
        ChestPlaceId = ChestPlaceId,
      };
      foreach (var rule in Rules)
      {
        copy.Rules.Add(rule.Duplicate());
      }

      return copy;
    }

    public static OrderSheet FromProto(global::Bnet.OrderSheet proto)
    {
      return Of(
        proto.Job, proto.Courage, proto.FallbackJob, proto.Overrides, proto.Rules,
        proto.HasAreaPlaceId ? proto.AreaPlaceId : 0, proto.HasChestPlaceId ? proto.ChestPlaceId : 0
      );
    }

    /// <summary>The sheet of orders as the server sent them for one bestia.</summary>
    public static OrderSheet FromProto(global::Bnet.BestiaAiConfigSMSG proto)
    {
      return Of(
        proto.Job, proto.Courage, proto.FallbackJob, proto.Overrides, proto.Rules,
        proto.HasAreaPlaceId ? proto.AreaPlaceId : 0, proto.HasChestPlaceId ? proto.ChestPlaceId : 0
      );
    }

    private static OrderSheet Of(
      Wire.Types.Job job,
      Wire.Types.Courage courage,
      Wire.Types.Job fallbackJob,
      Wire.Types.Overrides overrides,
      global::System.Collections.Generic.IEnumerable<Wire.Types.Rule> rules,
      ulong areaPlaceId,
      ulong chestPlaceId)
    {
      var sheet = new OrderSheet
      {
        Job = (int)job,
        Courage = (int)courage,
        FallbackJob = (int)fallbackJob,
        Overrides = OverridesFromProto(overrides),
        AreaPlaceId = areaPlaceId,
        ChestPlaceId = chestPlaceId,
      };
      foreach (var rule in rules)
      {
        sheet.Rules.Add(RuleFromProto(rule));
      }

      return sheet;
    }

    public global::Bnet.OrderSheet ToProto()
    {
      var proto = new global::Bnet.OrderSheet
      {
        Job = (Wire.Types.Job)Job,
        Courage = (Wire.Types.Courage)Courage,
        FallbackJob = (Wire.Types.Job)FallbackJob,
        Overrides = OverridesToProto(Overrides),
      };
      proto.Rules.AddRange(RulesToProto());
      if (AreaPlaceId != 0) proto.AreaPlaceId = AreaPlaceId;
      if (ChestPlaceId != 0) proto.ChestPlaceId = ChestPlaceId;

      return proto;
    }

    public global::System.Collections.Generic.IEnumerable<Wire.Types.Rule> RulesToProto()
    {
      foreach (var rule in Rules)
      {
        var wire = new Wire.Types.Rule
        {
          Condition = (Wire.Types.Rule.Types.Condition)(int)rule["condition"],
          Action = (Wire.Types.Rule.Types.Action)(int)rule["action"],
        };
        var threshold = (int)rule["threshold"];
        if (threshold >= 0) wire.Threshold = threshold;
        yield return wire;
      }
    }

    public static Dictionary OverridesFromProto(Wire.Types.Overrides proto)
    {
      var overrides = new Dictionary();
      if (proto == null) return overrides;

      if (proto.HasRetreatBelowHealthPct) overrides[OverrideKeys[0]] = proto.RetreatBelowHealthPct;
      if (proto.HasEatBelowStaminaPct) overrides[OverrideKeys[1]] = proto.EatBelowStaminaPct;
      if (proto.HasHealBelowHealthPct) overrides[OverrideKeys[2]] = proto.HealBelowHealthPct;
      if (proto.HasHaulAtLoadPct) overrides[OverrideKeys[3]] = proto.HaulAtLoadPct;
      if (proto.HasAreaRadius) overrides[OverrideKeys[4]] = proto.AreaRadius;
      return overrides;
    }

    public static Wire.Types.Overrides OverridesToProto(Dictionary overrides)
    {
      var proto = new Wire.Types.Overrides();
      if (overrides.TryGetValue(OverrideKeys[0], out var retreat)) proto.RetreatBelowHealthPct = (int)retreat;
      if (overrides.TryGetValue(OverrideKeys[1], out var eat)) proto.EatBelowStaminaPct = (int)eat;
      if (overrides.TryGetValue(OverrideKeys[2], out var heal)) proto.HealBelowHealthPct = (int)heal;
      if (overrides.TryGetValue(OverrideKeys[3], out var haul)) proto.HaulAtLoadPct = (int)haul;
      if (overrides.TryGetValue(OverrideKeys[4], out var radius)) proto.AreaRadius = (int)radius;
      return proto;
    }

    private static Dictionary RuleFromProto(Wire.Types.Rule rule)
    {
      return new Dictionary
      {
        ["condition"] = (int)rule.Condition,
        ["action"] = (int)rule.Action,
        ["threshold"] = rule.HasThreshold ? rule.Threshold : -1,
      };
    }
  }
}
