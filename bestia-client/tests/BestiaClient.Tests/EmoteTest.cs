using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.RegularExpressions;
using BestiaBehemothClient.Bnet.Message.Entity;
using Google.Protobuf.Reflection;
using AnimationKind = Bnet.AnimationKind;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// Emotes are named three ways - the server's AnimationKind, the name AnimationComponentSMSG hands GDScript, and the
  /// kind an emote resource in Game/Emote/DB picks in the inspector - and the three have to agree for an emote to play.
  /// </summary>
  public class EmoteTest
  {
    [Fact]
    public void EveryKindSurvivesTheTripToGdscriptAndBack()
    {
      foreach (var kind in Enum.GetValues<AnimationKind>())
      {
        Assert.Equal(kind, EmoteCMSG.ToKind(AnimationComponentSMSG.KindName(kind)));
      }
    }

    /// <summary>
    /// GDScript keys its clips by the proto's name. The trip above cannot catch a misspelling, since parsing ignores
    /// case and underscores: PICK_UP once arrived as "PICKUP" and no master ever bent down.
    /// </summary>
    [Fact]
    public void EveryKindReachesGdscriptAsTheProtoSpellsIt()
    {
      foreach (var kind in Enum.GetValues<AnimationKind>())
      {
        var proto = typeof(AnimationKind).GetField(kind.ToString())!.GetCustomAttribute<OriginalNameAttribute>()!.Name;
        Assert.Equal(proto, AnimationComponentSMSG.KindName(kind));
      }
    }

    [Fact]
    public void AKindFromANewerServerReadsAsIdle()
    {
      Assert.Equal("IDLE", AnimationComponentSMSG.KindName((AnimationKind)999));
    }

    [Fact]
    public void EveryEmoteResourceNamesARealKindWithATranslatedLabel()
    {
      var kinds = Enum.GetValues<AnimationKind>().Select(AnimationComponentSMSG.KindName).ToHashSet();
      var rows = File.ReadAllLines(Path.Combine(AppContext.BaseDirectory, "general.csv"))
        .Select(line => line.Split(',', 2)[0])
        .ToHashSet();
      var files = Directory.GetFiles(Path.Combine(AppContext.BaseDirectory, "EmoteDB"), "*.tres");

      Assert.NotEmpty(files);
      foreach (var file in files)
      {
        var text = File.ReadAllText(file);
        // An emote left on the dropdown's default is not written at all; it is CHEER.
        var kind = Value(text, "kind") ?? "CHEER";
        Assert.True(kinds.Contains(kind), $"{Path.GetFileName(file)} names {kind}, which is no AnimationKind");
        Assert.True(rows.Contains(Value(text, "label_key") ?? ""), $"{Path.GetFileName(file)} has no general.csv label");
      }
    }

    private static string Value(string tres, string property)
    {
      var match = Regex.Match(tres, $"^{property} = \"([^\"]*)\"", RegexOptions.Multiline);
      return match.Success ? match.Groups[1].Value : null;
    }
  }
}
