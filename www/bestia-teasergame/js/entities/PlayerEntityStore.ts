import { Subject } from 'rxjs';

import { EntityStore, EntityUpdate } from './EntityStore';
import { Entity } from './Entity';
import { ComponentType } from './components/ComponentType';
import { PlayerComponent } from './components/PlayerComponent';
import { AccountInfo } from '../model/AccountInfo';

export class PlayerEntityManager {

  public activeEntity?: Entity;
  public masterEntity?: Entity;
  public ownedEntities: Entity[] = [];

  public readonly onNewActiveEntity = new Subject<Entity>();
  public readonly onNewMasterEntity = new Subject<Entity>();
  public readonly onEntitiesChanged = new Subject<Entity[]>();

  constructor(
    private readonly info: AccountInfo,
    entityStore: EntityStore
  ) {
    entityStore.onUpdateEntity.subscribe(this.checkEntity);
  }

  private addEntity(entity: Entity) {
    if (this.ownedEntities.find(x => x.id === entity.id)) {
      return;
    }

    this.ownedEntities.push(entity);
    this.onEntitiesChanged.next(this.ownedEntities);
  }

  private checkEntity(data: EntityUpdate) {
    const playerComp = data.entity.getComponent(ComponentType.PLAYER) as PlayerComponent;
    if (playerComp === null || playerComp.ownerAccountId === this.info.accountId) {

    }
  }
}
