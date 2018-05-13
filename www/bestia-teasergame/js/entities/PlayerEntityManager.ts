
class PlayerEntityManager {

  public playerEntityIds: number[] = [];
  public activePlayerEntityId?: number;

  constructor(
    private readonly accountId: number
  ) {
  }

  public processMessage(message: any) {

  }
}
