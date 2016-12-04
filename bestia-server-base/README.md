==== Bestia Server Base ====

The base is needed in order to have a preconfiguration POM managing all the dependencies of Spring.
Limitations of maven dont allow to keep also sources in this project thus we need a second common
lib project to hold all the shared components.