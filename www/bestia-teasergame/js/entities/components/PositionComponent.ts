import { Component, ComponentType } from "./Component";
import { Point } from "../Point";

export class PositionComponent extends Component {

  constructor(
    id: Number,
    entityId: Number,
    public position: Point
  ) {
    super(id, entityId, ComponentType.POSITION)
  }
}