import { Action } from './actions/Action';
import { Component, ComponentType } from './components/Component';

export class Entity {
 
  public newComponents: Component[] = [];
  public removedComponents: Component[] = [];

  private componentsKeyId = new Map<Number, Component>();
  private componentsKeyType = new Map<ComponentType, Component>();

  public gameData : any = {};

  public actions: Action[] = [];

  constructor(
    public readonly id: Number
  ) {

  }

  getComponentIterator(): IterableIterator<Component> {
    return this.componentsKeyId.values()
  }

  addComponent(component: Component) {
    this.componentsKeyId.set(component.id, component);
    this.componentsKeyType.set(component.type, component);
    this.newComponents.push(component);
  }

  getComponent(type: ComponentType) {
    return this.componentsKeyType.get(type);
  }

  removeComponent(componentId: Number) {
    const removedComponent = this.componentsKeyId.get(componentId);
    this.removedComponents.push(removedComponent);
    this.componentsKeyId.delete(componentId);
    this.componentsKeyType.delete(removedComponent.type);
  }
}