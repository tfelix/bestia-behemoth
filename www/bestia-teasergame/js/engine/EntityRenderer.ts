import * as LOG from 'loglevel';

import { EntityStore } from "../entities/EntityStore";
import { ComponentType, Component } from "../entities/components/Component";
import { ComponentRenderer } from "./component/ComponentRenderer";
import { VisualComponentRenderer } from "./component/VisualComponentRenderer";
import { Entity } from '../entities/Entity';

export class EntityRenderer {

  private componentRenderer = new Map<ComponentType, ComponentRenderer<Component>>();

  constructor(
    private readonly game: Phaser.Scene,
    private readonly entityStore: EntityStore
  ) {
    this.addComponentRenderer(new VisualComponentRenderer(game));
  }

  private addComponentRenderer(renderer: ComponentRenderer<Component>) {
    this.componentRenderer.set(renderer.supportedComponent, renderer);
  }

  update() {
    this.updateNew();

    this.entityStore.removedEntities.forEach(e => this.remove(e));
    this.entityStore.removedEntities = [];
  }

  private remove(entity: Entity) {

  }

  private updateNew() {
    this.entityStore.newEntities.forEach(entity => {
      
      for(let component of entity.getComponentIterator()) {
        const renderer = this.componentRenderer.get(component.type);
        if(renderer != null) {
          renderer.render(entity, component);
        }
      }
    });
    this.entityStore.newEntities = [];
  }
}