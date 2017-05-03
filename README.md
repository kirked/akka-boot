akka-boot is a small, focused library to startup an actor system
and top-level actors using only configuration.

There are no dependencies other than Akka.

Scala object factory and Java static factory method implementations are supported.

### Configuration

To use akka-boot, supply an additional top-level configuration object named `boot` with the following values:

> **name** _(required)_ - The name of the actor system.
>
> **actors** _(required)_ - A list of actor configurations to start (see below).
>
> **abort-on-failure** _(optional)_ - A Boolean indicating whether or not to abort startup on failures. _(default **true**)_

#### Actor Configuration

See `src/test/resources/application.conf` for small examples of configurations. 
Each actor configuration is a configuration object with the following values:

> **name** _(required)_ - The name for the top-level actor.
>
> **generator** _(required)_ - A URI indicating how to construct the actor, with these supported formats:
>   * `class:FQCN`
>   * `factory:FQCN/method`
>
> **enabled** _(optional)_ - A Boolean indicating whether or not this actor should be started. _(default **true**)_
>
> **config** _(optional)_ - A configuration object to initialise the actor, either as a parameter or via message. _(default **empty configuration**)_
>
> **config-as-param** _(optional)_ - A Boolean indicating that the actor configuration should be provided as a construction parameter. _(default **false**)_
>
> **config-as-message** _(optional)_ - A Boolean indicating that the actor configuration should be provided as a message. _(default **false**)_

The actor configuration is opaque to akka-boot, so any values may be placed inside.
This allows multiple instances of the same actor class to be provisioned differently.

The `boot` configuration will not be provided to the actor system.

### Use

Simply use `akkaboot.Main` as your program's main entry point.

Until the actor system is started, any problems will be reported to standard error. Once the actor system is started, Akka logging is used to provide information & diagnostics.

### License

MIT.
