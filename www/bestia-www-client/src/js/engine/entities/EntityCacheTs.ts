interface ComponentSyncMessage { 
	ct: String, 
	payload: any, 
	l: number, 
	eid: number 
  } 
   
  class Entity { 
	 
	private components = {} 
	 
	constructor( 
	  private readonly id: number 
	) { 
   
	} 
   
	public setComponent(component: Component) { 
	  this.components[component.id] = component 
	} 
  } 
   
  class Component { 
   
	constructor( 
	  public readonly id: number 
	) { 
	   
	} 
  } 
   
  class VisualComponent extends Component { 
   
	public spriteInfo: SpriteInfo 
   
   
  } 
   
  enum VisualType { 
	SINGLE, 
	PACK, 
	ITEM, 
	DYNAMIC, 
	OBJECT 
  } 
   
  class SpriteInfo { 
   
	constructor( 
	  public sprite: String, 
	  public type: VisualType 
	) { 
   
	} 
  } 
   
  class EntityCacheTs { 
   
	constructor( 
	  private readonly pubsub: any 
	) { 
	   
	} 
   
	private onComponentUpdate(msg: any) { 
   
	} 
   
	private onComponentRemove(msg: any) { 
   
	} 
  }