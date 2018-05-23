import { Component } from './Component';
import { Point } from '../../model/Point';
import { ComponentType } from './ComponentType';

export class PositionComponent extends Component {

  public position: Point;
  public isSightBlocked: boolean;

  constructor(
    id: number,
    entityId: number
  ) {
    super(id, entityId, ComponentType.POSITION);
  }
}
