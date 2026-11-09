using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>The step rule over building pieces, against the server's <c>StructureWalkTest</c>.</summary>
  public class StructureWalkRulesTest
  {
    private const int Size = TerrainFixtures.Size;
    private const int Height = TerrainFixtures.Height;
    private const byte Granite = 4;

    /// <summary>Granite up to voxel 39, so the ground is stood on at 40.</summary>
    private static FakeChunkSource FlatGround()
    {
      var blocks = new byte[Size * Size * Height];
      var occupancy = new byte[Size * Size * Height];
      for (var column = 0; column < Size * Size; column++)
      {
        for (var z = 0; z < 40; z++)
        {
          blocks[column * Height + z] = Granite;
          occupancy[column * Height + z] = 255;
        }
      }

      var source = new FakeChunkSource();
      source.Put(new VoxelChunk(0, 0, 0, Size, Height, blocks, occupancy));
      return source;
    }

    /// <summary>Pieces as a batch delivers them: Godot's axis order, so the server's y is z.</summary>
    private static StructureCells Pieces(params (int Kind, int X, int Y, int Z, float Yaw)[] pieces)
    {
      var entries = new System.Collections.Generic.List<ChunkStaticEntitiesSMSG.Entry>();
      for (var i = 0; i < pieces.Length; i++)
      {
        var piece = pieces[i];
        entries.Add(new ChunkStaticEntitiesSMSG.Entry
        {
          EntityId = i + 1, Kind = piece.Kind, Position = new Vector3I(piece.X, piece.Z, piece.Y), Yaw = piece.Yaw
        });
      }

      var cells = new StructureCells();
      cells.ReplaceColumn(new ChunkKey(0, 0, 0), entries);
      return cells;
    }

    private static WalkRules.Step Check(
      StructureCells cells, long fromX, long fromY, double fromZ, long toX, long toY, out double landing)
    {
      return WalkRules.Check(FlatGround(), ChunkWrap.None, Size, Height, fromX, fromY, fromZ, toX, toY, out landing, cells);
    }

    [Fact]
    public void AWallRefusesTheStepThroughItButNotPastItsEnd()
    {
      var cells = Pieces((StructurePieces.Wall, 5, 5, 40, 0f));

      Assert.Equal(WalkRules.Step.Refused, Check(cells, 5, 5, 40, 6, 5, out _));
      Assert.Equal(WalkRules.Step.Refused, Check(cells, 6, 6, 40, 5, 6, out _));
      Assert.Equal(WalkRules.Step.Allowed, Check(cells, 5, 7, 40, 6, 7, out _));
    }

    [Fact]
    public void AFloorOnTheGroundIsAHalfStepUp()
    {
      var cells = Pieces((StructurePieces.Floor, 6, 8, 40, 0f));

      Assert.Equal(WalkRules.Step.Allowed, Check(cells, 5, 8, 40, 6, 8, out var landing));
      Assert.Equal(40.5, landing, 2);
    }

    [Fact]
    public void AMetreOfBuiltLedgeIsRefusedWhereAMetreOfTerrainIsNot()
    {
      // The stairs' second tile is a full cell standing on the ground: a metre up from beside it.
      var cells = Pieces((StructurePieces.Stairs, 10, 10, 40, 0f));

      Assert.Equal(WalkRules.Step.Refused, Check(cells, 11, 9, 40, 11, 10, out _));
      Assert.Equal(WalkRules.Step.Allowed, Check(cells, 10, 10, 40.5, 11, 10, out var landing));
      Assert.Equal(41.0, landing, 2);
    }

    [Fact]
    public void ATurnedWallStandsOnTheTurnedBorder()
    {
      var cells = Pieces((StructurePieces.Wall, 5, 5, 40, Mathf.Pi / 2));

      Assert.Equal(WalkRules.Step.Refused, Check(cells, 5, 5, 40, 5, 4, out _));
      Assert.Equal(WalkRules.Step.Allowed, Check(cells, 5, 5, 40, 6, 5, out _));
    }
  }
}
