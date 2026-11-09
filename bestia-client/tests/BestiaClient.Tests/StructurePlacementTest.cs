using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>Where a building piece snaps to under a ray, in server coordinates.</summary>
  public class StructurePlacementTest
  {
    private const int Size = TerrainFixtures.Size;
    private const int Height = TerrainFixtures.Height;
    private const byte Granite = 4;

    /// <summary>Granite up to voxel 39, so the first free cell above the ground is 40.</summary>
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

    private static StructureCells Pieces(params (int Kind, int X, int Y, int Z)[] pieces)
    {
      var entries = new List<ChunkStaticEntitiesSMSG.Entry>();
      for (var i = 0; i < pieces.Length; i++)
      {
        var piece = pieces[i];
        entries.Add(new ChunkStaticEntitiesSMSG.Entry
        {
          EntityId = i + 1, Kind = piece.Kind, Position = new Vector3I(piece.X, piece.Z, piece.Y)
        });
      }

      var cells = new StructureCells();
      cells.ReplaceColumn(new ChunkKey(0, 0, 0), entries);
      return cells;
    }

    private static StructurePlacement.Spot? Solve(
      StructureCells cells, int kind, double fromX, double fromY, double fromZ, double dirX, double dirY, double dirZ)
    {
      return StructurePlacement.Solve(
        FlatGround(), ChunkWrap.None, Size, Height, cells, kind, fromX, fromY, fromZ, dirX, dirY, dirZ, 0, 80);
    }

    [Fact]
    public void AFloorSnapsOntoTheGround()
    {
      var spot = Solve(new StructureCells(), StructurePieces.Floor, 10.5, 10.5, 50, 0, 0, -1);

      Assert.Equal(new StructurePlacement.Spot(10, 10, 40, true), spot);
    }

    [Fact]
    public void AFloorAimedAtTheTopOfAWallGoesOnTopOfIt()
    {
      var cells = Pieces((StructurePieces.Wall, 5, 5, 40));

      var spot = Solve(cells, StructurePieces.Floor, 8.5, 5.5, 42.6, -1, 0, 0);

      Assert.Equal(new StructurePlacement.Spot(6, 5, 43, true), spot);
    }

    [Fact]
    public void APieceAimedLowOnAWallGoesInFrontOfIt()
    {
      var cells = Pieces((StructurePieces.Wall, 5, 5, 40));

      var spot = Solve(cells, StructurePieces.Floor, 8.5, 5.5, 40.5, -1, 0, 0);

      Assert.Equal(new StructurePlacement.Spot(6, 5, 40, true), spot);
    }

    [Fact]
    public void AWallOnAFloorStandsInTheFloorsCell()
    {
      var cells = Pieces((StructurePieces.Floor, 10, 10, 40));

      var spot = Solve(cells, StructurePieces.Wall, 10.5, 10.5, 50, 0, 0, -1);

      Assert.Equal(new StructurePlacement.Spot(10, 10, 40, true), spot);
    }

    [Fact]
    public void AFloorOverlappingAnotherIsRefused()
    {
      var cells = Pieces((StructurePieces.Floor, 10, 10, 40));

      var spot = Solve(cells, StructurePieces.Floor, 9.5, 9.5, 50, 0, 0, -1);

      Assert.Equal(new StructurePlacement.Spot(9, 9, 40, false), spot);
    }

    [Fact]
    public void ARayIntoTheSkyFindsNothing()
    {
      Assert.Null(Solve(new StructureCells(), StructurePieces.Floor, 10.5, 10.5, 50, 0, 0, 1));
    }
  }
}
