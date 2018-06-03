import { Component, ComponentType } from '.';

export class ConditionComponent extends Component {

  public currentHealth = 0;
  public maxHealth = 0;
  public currentMana = 0;
  public maxMana = 0;

  constructor(
    id: number,
    entityId: number
  ) {
    super(id, entityId, ComponentType.CONDITION);
  }
}
