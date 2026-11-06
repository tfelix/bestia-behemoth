using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Dev
{
  /// <summary>
  /// Every tree model at each stage of growing back, drawn by the game's own prop renderer.
  /// </summary>
  /// <remarks>
  /// Columns are the ten models. Rows, front to back: stump, sapling, young tree, grown tree. The heights are
  /// what the server sends for a tree of <c>--grown</c> decimetres with the stage shares of
  /// <c>prop-kinds.yml</c>, so this shows what a player sees.
  ///
  /// <para>
  /// <c>DISPLAY=:0 godot --path bestia-client/src res://Dev/TreeStageHarness/TreeStageHarness.tscn --
  /// [--view=stumps] [--grown=60] [--out=/tmp/tree_stages.png]</c>. <c>--grown</c> is the grown height in
  /// decimetres; the generator's trees are 45 to 120.
  /// </para>
  /// </remarks>
  public partial class TreeStageHarness : Node3D
  {
    /// <summary><c>StaticEntityKind.TREE</c>.</summary>
    private const int Tree = 0;

    private const int Models = 10;

    /// <summary>Metres between two models in a row, and between two rows.</summary>
    private const int ColumnStep = 6;
    private const int RowStep = 7;

    private uint _grownHeightDm = 60;

    private readonly Dictionary<string, string> _args = new();

    public override async void _Ready()
    {
      foreach (var arg in OS.GetCmdlineUserArgs())
      {
        var parts = arg.TrimStart('-').Split('=', 2);
        _args[parts[0]] = parts.Length > 1 ? parts[1] : "true";
      }

      if (_args.TryGetValue("grown", out var grown))
      {
        _grownHeightDm = uint.Parse(grown);
      }

      BuildStage(_args.GetValueOrDefault("view", "all"));

      var renderer = new StaticEntityRenderer();
      AddChild(renderer);
      // Five models to a chunk, because a chunk is 32 m across and a row of ten would not fit in one.
      renderer.Apply(ChunkStaticEntitiesSMSG.FromProto(Batch(chunkX: 0, firstModel: 0)));
      renderer.Apply(ChunkStaticEntitiesSMSG.FromProto(Batch(chunkX: 1, firstModel: 5)));

      if (_args.TryGetValue("out", out var outPath))
      {
        await ToSignal(GetTree().CreateTimer(2.0), SceneTreeTimer.SignalName.Timeout);
        GetViewport().GetTexture().GetImage().SavePng(outPath);
        GD.Print($"[tree-stage-harness] wrote {outPath}");
        GetTree().Quit();
      }
    }

    private global::Bnet.ChunkStaticEntitiesSMSG Batch(int chunkX, int firstModel)
    {
      var batch = new global::Bnet.ChunkStaticEntitiesSMSG { Pos = new global::Bnet.ChunkPos { X = chunkX } };

      var rows = new[]
      {
        (global::Bnet.GrowthStage.Grown, 1f),
        (global::Bnet.GrowthStage.Young, 0.65f),
        (global::Bnet.GrowthStage.Sapling, 0.35f),
        (global::Bnet.GrowthStage.Stump, 1f)
      };

      for (var row = 0; row < rows.Length; row++)
      {
        var (stage, share) = rows[row];

        for (var column = 0; column < 5; column++)
        {
          batch.Entries.Add(new global::Bnet.ChunkStaticEntitiesSMSG.Types.Entry
          {
            EntityId = (ulong)(1000 * row + firstModel + column + 1),
            Kind = Tree,
            Variant = (uint)(firstModel + column),
            LocalX = (uint)(3 + ColumnStep * column),
            LocalY = (uint)(3 + RowStep * row),
            HeightDm = (uint)Mathf.RoundToInt(_grownHeightDm * share),
            Growth = stage
          });
        }
      }

      return batch;
    }

    private void BuildStage(string view)
    {
      var environment = new Environment
      {
        BackgroundMode = Environment.BGMode.Color,
        BackgroundColor = new Color(0.62f, 0.75f, 0.9f),
        AmbientLightSource = Environment.AmbientSource.Color,
        AmbientLightColor = new Color(0.62f, 0.66f, 0.72f),
        TonemapMode = Environment.ToneMapper.Filmic
      };
      AddChild(new WorldEnvironment { Environment = environment });

      AddChild(new DirectionalLight3D { RotationDegrees = new Vector3(-50, 35, 0), ShadowEnabled = true });

      var ground = new MeshInstance3D
      {
        Mesh = new PlaneMesh { Size = new Vector2(160, 120) },
        MaterialOverride = new StandardMaterial3D { AlbedoColor = new Color(0.3f, 0.45f, 0.22f) },
        Position = new Vector3(32, 0, 14)
      };
      AddChild(ground);

      var camera = new Camera3D { Current = true, Fov = 50 };
      AddChild(camera);

      var stumpRow = 3 + RowStep * 3;
      if (view == "stumps")
      {
        camera.Position = new Vector3(31, 13, stumpRow + 34);
        camera.LookAt(new Vector3(31, 0, stumpRow - 1));
      }
      else
      {
        camera.Position = new Vector3(32, 16, stumpRow + 42);
        camera.LookAt(new Vector3(32, 2.5f, 12));
      }
    }
  }
}
