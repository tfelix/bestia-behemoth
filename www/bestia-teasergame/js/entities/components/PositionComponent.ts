import { Component } from './Component';
import { Point } from '../Point';
import { ComponentType } from './ComponentType';

export class PositionComponent extends Component {

  constructor(
    id: number,
    entityId: number,
    public position: Point
  ) {
    super(id, entityId, ComponentType.POSITION);
  }
}
