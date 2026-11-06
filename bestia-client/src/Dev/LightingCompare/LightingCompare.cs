using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Bnet.Message;
using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Dev
{
  /// <summary>
  /// Every tree model under the game's light, with chosen groups of lighting settings swapped for the plain light
  /// of a bare dev scene, to find out which of them change how the trees read.
  /// </summary>
  /// <remarks>
  /// The trees go through the game's own <see cref="StaticEntityRenderer"/> and the light is the game's own
  /// <c>Environment.tscn</c>, so a run without <c>--plain</c> is what a player sees. Each group named in
  /// <c>--plain</c> takes the plain scene's value instead: a flat colour background and ambient, Filmic, one white
  /// sun at 50 degrees and nothing screen-space or global.
  ///
  /// <para>
  /// <c>DISPLAY=:0 godot --path bestia-client/src --resolution 1600x900 res://Dev/LightingCompare/LightingCompare.tscn
  /// -- [--plain=all|tonemap,ambient,sky,sun,suncolour,shadows,sdfgi,ssil,ssao,fog] [--set=...] [--hour=13]
  /// [--kind=0] [--cloud=0] [--frames=700] [--out=/tmp/lighting.png]</c>. <c>--kind</c> is a
  /// <c>WeatherSMSG.Kind</c> ordinal.
  /// </para>
  /// </remarks>
  public partial class LightingCompare : Node3D
  {
    private static readonly string[] Groups =
      { "tonemap", "ambient", "sky", "sun", "suncolour", "shadows", "sdfgi", "ssil", "ssao", "fog" };

    /// <summary><c>StaticEntityKind.TREE</c>.</summary>
    private const int Tree = 0;

    /// <summary>Metres between two models in a row, and between two rows.</summary>
    private const int ColumnStep = 6;
    private const int RowStep = 7;

    /// <summary>Grown, young and sapling heights in decimetres, back to front.</summary>
    private static readonly uint[] RowHeightsDm = { 60, 39, 21 };

    /// <summary>
    /// SDFGI paints black blotches until it has settled, see <see cref="GrassBench"/>.
    /// </summary>
    private const int WarmupFrames = 700;

    private readonly Dictionary<string, string> _args = new();
    private HashSet<string> _plain = new();

    private Environment _environment;
    private DirectionalLight3D _sun;

    public override async void _Ready()
    {
      foreach (var arg in OS.GetCmdlineUserArgs())
      {
        var parts = arg.TrimStart('-').Split('=', 2);
        _args[parts[0]] = parts.Length > 1 ? parts[1] : "true";
      }

      var plain = _args.GetValueOrDefault("plain", "");
      _plain = plain == "all"
        ? Groups.ToHashSet()
        : plain.Split(',', System.StringSplitOptions.RemoveEmptyEntries).ToHashSet();

      // environment.gd writes the sun, the ambient energy and the sky on its own pass. Running after it every frame
      // keeps the plain and the --set values on screen.
      ProcessPriority = 100;

      _environment = GetNode<WorldEnvironment>("Environment/WorldEnvironment").Environment;
      _sun = GetNode<DirectionalLight3D>("Environment/Sun");

      // Before environment.gd's first pass, which reads them.
      ApplySets("daynight");
      AnchorClockAndWeather();
      ApplyPlainEnvironment();
      BuildStage();

      var renderer = new StaticEntityRenderer();
      AddChild(renderer);
      // Five models to a chunk, because a chunk is 32 m across and a row of ten would not fit in one.
      renderer.Apply(ChunkStaticEntitiesSMSG.FromProto(Batch(chunkX: 0, firstModel: 0)));
      renderer.Apply(ChunkStaticEntitiesSMSG.FromProto(Batch(chunkX: 1, firstModel: 5)));

      var frames = _args.TryGetValue("frames", out var f) ? int.Parse(f) : WarmupFrames;
      for (var i = 0; i < frames; i++)
      {
        await ToSignal(GetTree(), SceneTree.SignalName.ProcessFrame);
      }

      if (_args.TryGetValue("out", out var outPath))
      {
        await ToSignal(RenderingServer.Singleton, RenderingServer.SignalName.FramePostDraw);
        GetViewport().GetTexture().GetImage().SavePng(outPath);
        GD.Print($"[lighting-compare] plain={plain} wrote {outPath}");
        GetTree().Quit();
      }
    }

    public override void _Process(double delta)
    {
      ApplySets("env", "sky", "leaves");

      if (_plain.Contains("sun"))
      {
        _sun.RotationDegrees = new Vector3(-50, 35, 0);
      }

      if (_plain.Contains("suncolour"))
      {
        _sun.LightColor = Colors.White;
        _sun.LightEnergy = 1.0f;
      }

      if (_plain.Contains("ambient"))
      {
        _environment.AmbientLightEnergy = 1.0f;
      }
    }

    /// <summary>
    /// <c>--set=env.tonemap_mode=2,sky.gradient_curve=0.4,daynight.sun_day_colour=1/0.9/0.8,leaves.wrap=0.6</c>:
    /// the Environment resource, the sky shader, environment.gd's exports and both leaf materials.
    /// </summary>
    /// <remarks>environment.gd reads its exports on its next pass, so set those before the clock is anchored.</remarks>
    private void ApplySets(params string[] targets)
    {
      if (!_args.TryGetValue("set", out var assignments))
      {
        return;
      }

      foreach (var assignment in assignments.Split(',', System.StringSplitOptions.RemoveEmptyEntries))
      {
        var (key, text) = (assignment.Split('=')[0], assignment.Split('=')[1]);
        var (target, property) = (key.Split('.', 2)[0], key.Split('.', 2)[1]);
        if (!targets.Contains(target))
        {
          continue;
        }

        var value = Parse(text);
        switch (target)
        {
          case "env":
            _environment.Set(property, value);
            break;
          case "sky":
            ((ShaderMaterial)_environment.Sky.SkyMaterial).SetShaderParameter(property, value);
            break;
          case "daynight":
            GetNode("Environment").Set(property, value);
            break;
          case "leaves":
            GD.Load<ShaderMaterial>("res://Game/Terrain/Trees/leaves_common.tres").SetShaderParameter(property, value);
            GD.Load<ShaderMaterial>("res://Game/Terrain/Trees/leaves_pine.tres").SetShaderParameter(property, value);
            break;
        }
      }
    }

    private static Variant Parse(string text)
    {
      var invariant = System.Globalization.CultureInfo.InvariantCulture;
      if (text.Contains('/'))
      {
        var c = text.Split('/').Select(v => float.Parse(v, invariant)).ToArray();
        return new Color(c[0], c[1], c[2]);
      }

      if (bool.TryParse(text, out var flag))
      {
        return flag;
      }

      return text.Contains('.') ? float.Parse(text, invariant) : int.Parse(text, invariant);
    }

    /// <summary>Clear 13:00 on the full moon's day, unless <c>--hour</c>, <c>--kind</c> or <c>--cloud</c> say otherwise.</summary>
    private void AnchorClockAndWeather()
    {
      var invariant = System.Globalization.CultureInfo.InvariantCulture;
      var hour = double.Parse(_args.GetValueOrDefault("hour", "13"), invariant);

      var connection = GetNode("/root/ConnectionManager");
      connection.Get("world_clock").As<WorldClock>()?.Anchor(new WorldInfoSMSG
      {
        WorldAgeBestiaSeconds = 14 * 24 * 3600.0 + hour * 3600.0,
        TimeSpeedFactor = 0.0001,
        HoursPerDay = 24,
        DaysPerMonth = 30,
        MonthsPerYear = 4,
        NightEndHour = 4,
        DawnEndHour = 6,
        DuskStartHour = 20,
        NightStartHour = 22
      });

      var weather = connection.Get("weather").As<WeatherState>();
      if (weather != null)
      {
        weather.DebugOverrideEnabled = true;
        weather.DebugKind = (WeatherSMSG.Kind)int.Parse(_args.GetValueOrDefault("kind", "0"));
        weather.DebugCloudCover = float.Parse(_args.GetValueOrDefault("cloud", "0"), invariant);
        weather.DebugWindSpeed = 0.0f;
        weather.SkySeconds = 0.2f;
      }
    }

    /// <summary>The settings environment.gd never touches, set once.</summary>
    private void ApplyPlainEnvironment()
    {
      if (_plain.Contains("tonemap"))
      {
        _environment.TonemapMode = Environment.ToneMapper.Filmic;
      }

      if (_plain.Contains("ambient"))
      {
        _environment.AmbientLightSource = Environment.AmbientSource.Color;
        _environment.AmbientLightColor = new Color(0.62f, 0.66f, 0.72f);
      }

      if (_plain.Contains("sky"))
      {
        _environment.BackgroundMode = Environment.BGMode.Color;
        _environment.BackgroundColor = new Color(0.62f, 0.75f, 0.9f);
        _environment.ReflectedLightSource = Environment.ReflectionSource.Bg;
      }

      if (_plain.Contains("shadows"))
      {
        var defaults = new DirectionalLight3D();
        _sun.DirectionalShadowSplit1 = defaults.DirectionalShadowSplit1;
        _sun.DirectionalShadowSplit2 = defaults.DirectionalShadowSplit2;
        _sun.DirectionalShadowBlendSplits = defaults.DirectionalShadowBlendSplits;
        _sun.DirectionalShadowMaxDistance = defaults.DirectionalShadowMaxDistance;
        defaults.Free();
      }

      _environment.SdfgiEnabled &= !_plain.Contains("sdfgi");
      _environment.SsilEnabled &= !_plain.Contains("ssil");
      _environment.SsaoEnabled &= !_plain.Contains("ssao");
      _environment.FogEnabled &= !_plain.Contains("fog");
    }

    private static global::Bnet.ChunkStaticEntitiesSMSG Batch(int chunkX, int firstModel)
    {
      var batch = new global::Bnet.ChunkStaticEntitiesSMSG { Pos = new global::Bnet.ChunkPos { X = chunkX } };

      for (var row = 0; row < RowHeightsDm.Length; row++)
      {
        for (var column = 0; column < 5; column++)
        {
          batch.Entries.Add(new global::Bnet.ChunkStaticEntitiesSMSG.Types.Entry
          {
            EntityId = (ulong)(1000 * row + firstModel + column + 1),
            Kind = Tree,
            Variant = (uint)(firstModel + column),
            LocalX = (uint)(3 + ColumnStep * column),
            LocalY = (uint)(3 + RowStep * row),
            HeightDm = RowHeightsDm[row]
          });
        }
      }

      return batch;
    }

    /// <summary>The ground and the camera of the tree stage harness, so the two frames line up.</summary>
    private void BuildStage()
    {
      var ground = new MeshInstance3D
      {
        Mesh = new PlaneMesh { Size = new Vector2(160, 120) },
        MaterialOverride = new StandardMaterial3D { AlbedoColor = new Color(0.3f, 0.45f, 0.22f) },
        Position = new Vector3(32, 0, 14)
      };
      AddChild(ground);

      var camera = new Camera3D { Current = true, Fov = 50 };
      AddChild(camera);
      camera.Position = new Vector3(32, 16, 3 + RowStep * 3 + 42);
      camera.LookAt(new Vector3(32, 2.5f, 12));
    }
  }
}
