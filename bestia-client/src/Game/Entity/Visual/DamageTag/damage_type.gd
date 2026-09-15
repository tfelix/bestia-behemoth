class_name DamageType

## Mirrors DamageEntitySMSG.DamageType on the C# side. An exported C# enum reaches GDScript as a
## plain int, so these names exist to keep the dispatch in damage_tag.gd readable.

const MISS := 0
const NORMAL := 1
const CRIT := 2
const DODGE := 3
const HEAL := 4
