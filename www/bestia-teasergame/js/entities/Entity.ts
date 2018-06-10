import * as LOG from 'loglevel';

import { Action } from './actions/Action';
import { Component } from './components/Component';
import { ComponentType } from './components/ComponentType';
import { EntityData } from './EntityData';
import { EntityStore } from '.';
import { LOCAL_COMPONENT_ID } from 'engine/renderer/component/local/LocalComponent';

export class Entity {
  private readonly componentsKeyId = new Map<number, Component>();
  private readonly componentsKeyType = new Map<ComponentType, Component>();

  public readonly data = new EntityData();
  public actions: Action[] = [];

  public latency = 0;

  constructor(
    public readonly id: number,
    private readonly entityStore: EntityStore
  ) {

  }

  public getComponentIterator(): IterableIterator<Component> {
    return this.componentsKeyId.values();
  }

  public addComponent(component: Component) {
    if (component.id !== LOCAL_COMPONENT_ID && this.componentsKeyId.get(component.id)) {
      LOG.warn(`Component with id does already exist: ${component.id}`);
      return;
    }

    // We keep reference of non client only components so we can update and track them.
    // For client only components this kind of trackig is not needed.
    if (component.id !== LOCAL_COMPONENT_ID) {
      this.componentsKeyId.set(component.id, component);
    }

    this.componentsKeyType.set(component.type, component);
  }

  public getComponent(type: ComponentType) {
    return this.componentsKeyType.get(type);
  }

  public hasComponent(type: ComponentType): boolean {
    return this.componentsKeyType.has(type);
  }

  public removeComponent(componentId: number) {
    const removedComponent = this.componentsKeyId.get(componentId);
    this.componentsKeyId.delete(componentId);
    this.componentsKeyType.delete(removedComponent.type);
  }

  public removeComponentByType(type: ComponentType) {
    const component = this.componentsKeyType.get(type);
    if (component) {
      this.removeComponent(component.id);
    }
  }
}
