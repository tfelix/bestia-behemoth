using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using BestiaBehemothClient.Bnet.Message;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Dev
{
  /// <summary>
  /// A rolling meadow with the shipping terrain and grass on it, for measuring what the grass costs and for
  /// rendering it from a fixed viewpoint.
  /// </summary>
  /// <remarks>
  /// <b>It runs the game's own renderers, not copies.</b> Synthetic chunks go into a
  /// <see cref="ClientChunkStore"/> and through <see cref="TerrainRenderer"/>, which drives
  /// <see cref="TerrainGrass"/> exactly as <c>game.gd</c> wires it. Only the ground is invented.
  ///
  /// <para>
  /// Run it with a window, because the headless renderer draws nothing and measures nothing:
  /// <c>DISPLAY=:0 godot --path bestia-client/src --resolution 1600x900 res://Dev/GrassBench/GrassBench.tscn --
  /// --out=DIR [--label=name] [--budget=2000000] [--fade=150] [--reach=176] [--margin=20] [--chunks=11]
  /// [--patches | --patch-mask=png] [--pitch=-20] [--zoom=10] [--nolod] [--stats] [--gi-off] [--hide-grass] [--shot-only] [--walk=seconds]
  /// [--set=Property=value,...]</c>.
  /// </para>
  ///
  /// <para>
  /// Each run appends one row per yaw step to <c>bench.csv</c> in the output directory and saves a screenshot.
  /// The first row has the grass hidden, so the grass's own cost is the difference to it. One configuration per
  /// process, so no run inherits the GPU state of the one before it.
  /// </para>
  /// </remarks>
  public partial class GrassBench : Node3D
  {
    private const int ChunkSize = 32;
    private const int ChunkHeight = 64;

    private const byte Stone = 7;
    private const byte Dirt = 11;
    private const byte Grass = 13;

    /// <summary>Ground level at the centre, in voxels. Clear of the chunk floor and ceiling at any relief.</summary>
    private const double BaseElevation = 24.5;

    /// <summary>
    /// Frames before the first sample. SDFGI paints black blotches until it has settled, and its cost moves
    /// with it.
    /// </summary>
    private const int WarmupFrames = 700;

    /// <summary>Frames after a camera turn before sampling: the budget controller settles in a handful.</summary>
    private const int SettleFrames = 30;

    private const int SampleFrames = 90;

    private const int YawStepDegrees = 30;

    private readonly Dictionary<string, string> _args = new();

    private TerrainRenderer _terrain;
    private TerrainGrass _grass;
    private Camera3D _camera;
    private FastNoiseLite _relief;
    private FastNoiseLite _patches;

    /// <summary>Bare columns from <c>--patch-mask</c>, one pixel per metre centred on the meadow, or null.</summary>
    private Image _patchMask;
    private Vector3 _focus;

    public override void _Ready()
    {
      foreach (var arg in OS.GetCmdlineUserArgs())
      {
        var parts = arg.TrimStart('-').Split('=', 2);
        _args[parts[0]] = parts.Length > 1 ? parts[1] : "true";
      }

      DisplayServer.WindowSetVsyncMode(DisplayServer.VSyncMode.Disabled);
      Engine.MaxFps = 0;
      RenderingServer.ViewportSetMeasureRenderTime(GetViewport().GetViewportRid(), true);

      if (_args.ContainsKey("nolod"))
      {
        GetViewport().DebugDraw = Viewport.DebugDrawEnum.DisableLod;
      }

      _relief = new FastNoiseLite { Seed = 7, Frequency = 1.0f / 90.0f, FractalOctaves = 3 };
      _patches = new FastNoiseLite
      {
        Seed = 11,
        Frequency = 1.0f / 25.0f,
        FractalOctaves = 2,
        DomainWarpEnabled = true,
        DomainWarpAmplitude = 12.0f
      };

      // A mask exported from the generator, so the bench draws the world's own patches rather than its own.
      if (_args.TryGetValue("patch-mask", out var maskPath))
      {
        _patchMask = Image.LoadFromFile(maskPath);
      }

      _camera = GetNode<Camera3D>("Camera3D");

      // Created as game.gd does it, so the bench measures the same nodes the game draws with.
      _terrain = new TerrainRenderer { Name = "Terrain" };
      AddChild(_terrain);
      _grass = new TerrainGrass { Name = "Grass" };
      AddChild(_grass);
      _terrain.Grass = _grass;

      Configure(_grass);

      _ = Run();
    }

    private void Configure(TerrainGrass grass)
    {
      grass.MaxVisibleTriangles = Int("budget", grass.MaxVisibleTriangles);
      grass.FadeOutMetres = Float("fade", grass.FadeOutMetres);
      grass.ReachMetres = Float("reach", grass.ReachMetres);
      grass.ViewMarginDegrees = Float("margin", grass.ViewMarginDegrees);

      // Any other export by name, for the toggles that do not earn a flag of their own.
      if (_args.TryGetValue("set", out var assignments))
      {
        foreach (var assignment in assignments.Split(',', StringSplitOptions.RemoveEmptyEntries))
        {
          var (name, value) = (assignment.Split('=')[0], assignment.Split('=')[1]);
          var current = grass.Get(name);
          grass.Set(name, current.VariantType switch
          {
            Variant.Type.Bool => bool.Parse(value),
            Variant.Type.Int => int.Parse(value, CultureInfo.InvariantCulture),
            _ => float.Parse(value, CultureInfo.InvariantCulture)
          });
        }
      }
    }

    private async Task Run()
    {
      AnchorClockAtNoon();
      BuildMeadow();

      _focus = new Vector3(0.0f, (float)ElevationAt(0, 0), 0.0f);
      _grass.SetFocusAt(_focus);
      AddPlayerStandIn();
      Aim(0.0f);

      while (!_terrain.IsIdle || _grass.PendingChunks > 0)
      {
        await Frames(1);
      }

      await Frames(Int("warmup", WarmupFrames));

      var outDir = _args.GetValueOrDefault("out", ProjectSettings.GlobalizePath("user://grass_bench"));
      Directory.CreateDirectory(outDir);
      var label = _args.GetValueOrDefault("label", "run");

      if (_args.ContainsKey("walk"))
      {
        await Walk(Path.Combine(outDir, $"{label}_walk"), Float("walk", 8.0f));
      }
      else if (!_args.ContainsKey("shot-only"))
      {
        var rows = new List<string>();

        _grass.Visible = false;
        rows.Add(Row(label, -1, await Sample()));
        _grass.Visible = true;

        for (var yaw = 0; yaw < 360; yaw += YawStepDegrees)
        {
          Aim(yaw);
          await Frames(SettleFrames);
          rows.Add(Row(label, yaw, await Sample()));
        }

        Append(Path.Combine(outDir, "bench.csv"), rows);
        Aim(0.0f);
        await Frames(SettleFrames);
      }

      // The render-debug readout is an overlay on the viewport, so it would land in the image - unless the
      // image is meant to show its frame times.
      var debug = GetNodeOrNull("/root/RenderDebug");
      if (_args.ContainsKey("gi-off"))
      {
        debug?.Call("_toggle_all_gi");
        await Frames(120);
      }

      if (_args.ContainsKey("hide-grass"))
      {
        _grass.Visible = false;
        await Frames(10);
      }

      if (_args.ContainsKey("stats"))
      {
        debug?.Call("_toggle_stats");
        await Frames(60);
      }
      else if (debug?.Get("_layer").As<CanvasLayer>() is { } readout)
      {
        readout.Visible = false;
        await Frames(2);
      }

      GetViewport().GetTexture().GetImage().SavePng(Path.Combine(outDir, $"{label}.png"));
      GD.Print($"[grass-bench] {label} done");
      GetTree().Quit();
    }

    private readonly struct Measured
    {
      public double GpuMean { get; init; }
      public double GpuP95 { get; init; }
      public double CpuMean { get; init; }
      public long Primitives { get; init; }
      public long DrawCalls { get; init; }
      public int Wanted { get; init; }
      public int Drawn { get; init; }
      public int OnScreen { get; init; }
      public float Exponent { get; init; }
      public float Fade { get; init; }
    }

    private async Task<Measured> Sample()
    {
      var viewport = GetViewport();
      var rid = viewport.GetViewportRid();
      var gpu = new List<double>(SampleFrames);
      var cpu = new List<double>(SampleFrames);
      long primitives = 0, draws = 0;

      for (var frame = 0; frame < SampleFrames; frame++)
      {
        await Frames(1);
        gpu.Add(RenderingServer.ViewportGetMeasuredRenderTimeGpu(rid));
        cpu.Add(RenderingServer.ViewportGetMeasuredRenderTimeCpu(rid) +
                Performance.GetMonitor(Performance.Monitor.TimeProcess) * 1000.0);
        primitives += viewport.GetRenderInfo(Viewport.RenderInfoType.Visible, Viewport.RenderInfo.PrimitivesInFrame);
        draws += viewport.GetRenderInfo(Viewport.RenderInfoType.Visible, Viewport.RenderInfo.DrawCallsInFrame);
      }

      gpu.Sort();

      return new Measured
      {
        GpuMean = gpu.Average(),
        GpuP95 = gpu[(int)(gpu.Count * 0.95)],
        CpuMean = cpu.Average(),
        Primitives = primitives / SampleFrames,
        DrawCalls = draws / SampleFrames,
        Wanted = _grass.WantedTriangles,
        Drawn = _grass.DrawnTriangles,
        OnScreen = _grass.OnScreenTriangles,
        Exponent = _grass.Exponent,
        Fade = _grass.FadeMetres
      };
    }

    private string Row(string label, int yaw, Measured m) => string.Join(',',
      label, _grass.MaxVisibleTriangles, _grass.FadeOutMetres, _grass.ReachMetres, _grass.ViewMarginDegrees,
      yaw < 0 ? "nograss" : yaw.ToString(CultureInfo.InvariantCulture),
      F(m.GpuMean), F(m.GpuP95), F(m.CpuMean), m.Primitives, m.DrawCalls, m.Wanted, m.Drawn, m.OnScreen, F(m.Exponent), F(m.Fade));

    private static void Append(string path, List<string> rows)
    {
      if (!File.Exists(path))
      {
        File.WriteAllText(path,
          "label,budget,fade,reach,margin,yaw,gpu_ms,gpu_p95_ms,cpu_ms,primitives,draw_calls,grass_wanted,grass_drawn,grass_on_screen," +
          "exponent,fade_used\n");
      }

      File.AppendAllLines(path, rows);
    }

    /// <summary>
    /// Fixes the hour at noon. <c>environment.gd</c> draws no sky until the clock is anchored, and a clock left
    /// running would light each shot differently.
    /// </summary>
    private void AnchorClockAtNoon()
    {
      var clock = GetNode("/root/ConnectionManager").Get("world_clock").As<WorldClock>();
      clock?.Anchor(new WorldInfoSMSG
      {
        WorldAgeBestiaSeconds = 13.0 * 3600.0,
        TimeSpeedFactor = 0.0001,
        HoursPerDay = 24,
        DaysPerMonth = 30,
        MonthsPerYear = 4,
        NightEndHour = 4,
        DawnEndHour = 6,
        DuskStartHour = 20,
        NightStartHour = 22
      });
    }

    private void BuildMeadow()
    {
      var store = new ClientChunkStore();
      var radius = Int("chunks", 11) / 2;
      var keys = new List<ChunkKey>();

      for (var chunkY = -radius; chunkY <= radius; chunkY++)
      {
        for (var chunkX = -radius; chunkX <= radius; chunkX++)
        {
          var key = new ChunkKey(chunkX, chunkY, 0);
          store.Put(key, BuildChunk(chunkX, chunkY), 1);
          keys.Add(key);
        }
      }

      _terrain.Configure(store, null);

      foreach (var key in keys)
      {
        _terrain.Invalidate(key);
      }
    }

    /// <summary>A grass cap over dirt over stone, as the generator writes a grassland column.</summary>
    private VoxelChunk BuildChunk(int chunkX, int chunkY)
    {
      var blocks = new byte[ChunkSize * ChunkSize * ChunkHeight];
      var occupancy = new byte[ChunkSize * ChunkSize * ChunkHeight];
      var patches = _args.ContainsKey("patches");

      for (var localY = 0; localY < ChunkSize; localY++)
      {
        for (var localX = 0; localX < ChunkSize; localX++)
        {
          var voxelX = chunkX * ChunkSize + localX;
          var voxelY = chunkY * ChunkSize + localY;

          var elevation = ElevationAt(voxelX, voxelY);
          var top = (int)Math.Floor(elevation);
          var offset = (localY * ChunkSize + localX) * ChunkHeight;

          var cap = IsBare(voxelX, voxelY, patches) ? Dirt : Grass;

          for (var z = 0; z < top; z++)
          {
            blocks[offset + z] = z >= top - 3 ? Dirt : Stone;
            occupancy[offset + z] = 255;
          }

          blocks[offset + top] = cap;
          occupancy[offset + top] = (byte)Math.Clamp((int)Math.Round((elevation - top) * 255.0), 1, 255);
        }
      }

      return new VoxelChunk(chunkX, chunkY, 0, ChunkSize, ChunkHeight, blocks, occupancy);
    }

    private bool IsBare(int voxelX, int voxelY, bool patches)
    {
      if (_patchMask != null)
      {
        var pixelX = voxelX + _patchMask.GetWidth() / 2;
        var pixelY = voxelY + _patchMask.GetHeight() / 2;

        return pixelX >= 0 && pixelY >= 0 && pixelX < _patchMask.GetWidth() && pixelY < _patchMask.GetHeight() &&
               _patchMask.GetPixel(pixelX, pixelY).R > 0.5f;
      }

      return patches && _patches.GetNoise2D(voxelX, voxelY) > 0.35f;
    }

    /// <summary>Gentle hills, a few metres high, so all of it is walkable and grows grass.</summary>
    private double ElevationAt(int voxelX, int voxelY) => BaseElevation + 6.0 * _relief.GetNoise2D(voxelX, voxelY);

    /// <summary>
    /// Runs the stand-in forward for <paramref name="seconds"/> of game time and saves every other frame, so the
    /// grass's level of detail can be judged in motion.
    /// </summary>
    /// <remarks>
    /// Fixed steps of a thirtieth of a second rather than real time, so the clip plays at running speed however
    /// slowly this machine renders it.
    /// </remarks>
    private async Task Walk(string frameDir, float seconds)
    {
      const float RunMetresPerSecond = 5.0f;
      const float Step = 1.0f / 30.0f;

      Directory.CreateDirectory(frameDir);

      if (GetNodeOrNull("/root/RenderDebug")?.Get("_layer").As<CanvasLayer>() is { } readout)
      {
        readout.Visible = false;
      }

      var player = GetNode<Node3D>("Player");
      var heading = Basis.FromEuler(new Vector3(0.0f, Mathf.DegToRad(Float("yaw", 0.0f)), 0.0f)) * Vector3.Forward;
      var start = _focus;

      for (var frame = 0; frame * Step < seconds; frame++)
      {
        var at = start + heading * RunMetresPerSecond * frame * Step;
        _focus = new Vector3(at.X, (float)ElevationAt(Mathf.RoundToInt(at.X), Mathf.RoundToInt(at.Z)), at.Z);
        _grass.SetFocusAt(_focus);
        player.Position = _focus + new Vector3(0.0f, 0.9f, 0.0f);
        Aim(0.0f);

        await Frames(1);

        if (frame % 2 == 0)
        {
          GetViewport().GetTexture().GetImage().SavePng(Path.Combine(frameDir, $"{frame / 2:0000}.png"));
        }
      }
    }

    /// <summary>A 1.8 m capsule where the player would stand, so a render shows the grass at its scale.</summary>
    private void AddPlayerStandIn()
    {
      AddChild(new MeshInstance3D
      {
        Name = "Player",
        Mesh = new CapsuleMesh { Radius = 0.3f, Height = 1.8f },
        Position = _focus + new Vector3(0.0f, 0.9f, 0.0f)
      });
    }

    /// <summary>Places the camera where the spring arm holds it behind a player at the centre.</summary>
    private void Aim(float yawDegrees)
    {
      var pitch = Mathf.DegToRad(Float("pitch", -20.0f));
      var yaw = Mathf.DegToRad(Float("yaw", 0.0f) + yawDegrees);
      var pivot = _focus + new Vector3(0.0f, 1.5f, 0.0f);
      var basis = Basis.FromEuler(new Vector3(pitch, yaw, 0.0f));

      _camera.GlobalTransform = new Transform3D(basis, pivot + basis.Z * Float("zoom", 10.0f));
    }

    private async Task Frames(int count)
    {
      for (var i = 0; i < count; i++)
      {
        await ToSignal(GetTree(), SceneTree.SignalName.ProcessFrame);
      }
    }

    private int Int(string name, int fallback) =>
      _args.TryGetValue(name, out var value) ? int.Parse(value, CultureInfo.InvariantCulture) : fallback;

    private float Float(string name, float fallback) =>
      _args.TryGetValue(name, out var value) ? float.Parse(value, CultureInfo.InvariantCulture) : fallback;

    private static string F(double value) => value.ToString("0.###", CultureInfo.InvariantCulture);
  }
}
