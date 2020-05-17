# WorldGen

Bestia integrates a specialized library called **WorldGen** for world creation. It is a generator which uses
clustered map chunks capable of dividing the world creation workload onto multiple, different machines. 
With this framework it should be possible to create millions of square kilometers without hitting any 
memory limit on the servers during the creation process.

In general, it works by creating pipelines which are then used to create and transform noise maps. After 
the noise was modified and generated the map data is created and saved to the Bestia database in chunks 
via the **Voxel** module.

## Usage

As this is a very flexible library there are a few steps and even some code which needs to be implemented 
before this library can be used to create maps. Please follow the steps below to get it going.

Also it might be helpful to have a look into the tests which perform some small map generations for testing
purpose but are great to start building on.

### Setup Components

TBD

### Prepare Generator Pipelines

TBD

### Create MapDescription

TBD

## Contributing

This is part of the Bestia mono repository. If you consider contributing please refer to the [main modules](http://github.com/tfelix/bestia-beghemoth) information.