akka-boot is a small, focused library to startup an actor system
and top-level actors using only configuration.

There are no dependencies other than Akka.

Scala object factory and Java static factory method implementations are supported
(as well as class-based reflection).


### Configuration

To use akka-boot, supply an additional top-level configuration object named `akka-boot`
with the following values:

> **name** _(required)_                - The name of the actor system.
>
> **actors** _(required)_              - A list of actor configurations to start (see below).
>
> **abort-on-failure** _(optional)_    - A Boolean indicating whether or not to abort
>                                        startup on failures. _(default **true**)_
>
> **exit-on-termination** _(optional)_ - A Boolean indicating whether or not to exit
>                                        the JVM when the actor system terminates.
>                                        _(default **true**)_

The `akka-boot` configuration will not be provided to the actor system.
After the top-level actors are started, the Boot actor will stop itself,
leaving only your running actors and optionally a VM exit termination hook.


#### Actor Configuration

See `src/test/resources/application.conf` for small examples of configurations. 
Each actor configuration is a configuration object with the following values:

> **name** _(required)_               - The name for the top-level actor.
>
> **generator** _(required)_          - A URI indicating how to construct the actor,
>                                       with these supported formats:
>
>   * `class:FQCN`            (reflective actor class creation)
>
>   * `factory:FQCN/method`   (Scala object or Java static factory creation)
>
>   * `supervisor:name/FQCN`  (supervised actor class creation)
>
> **enabled** _(optional)_            - A Boolean indicating whether or not this actor
>                                       should be started. _(default **true**)_
>
> **config** _(optional)_             - A configuration object to initialise the actor, either as
>                                       a parameter or via message. _(default **empty configuration**)_
>
> **config-as-param** _(optional)_    - A Boolean indicating that the actor configuration should be
>                                       provided as a construction parameter. _(default **false**)_
>
> **config-as-message** _(optional)_  - A Boolean indicating that the actor configuration should
>                                       be provided as a message. _(default **false**)_

The `config` element of the actor configuration is opaque to akka-boot,
so any values may be placed inside. This allows multiple instances of the
same actor class to be provisioned differently.


#### Supervisor Configuration

Supervisors may be configured with the following values (note that defaults
for optional parameters are the Akka defaults):

> **name** _(required)_               - The name of the supervisor.
>
> **strategy** _(required)_           - The error-handling strategy for the supervisor,
>                                       one of:
>
>   * `one-for-one`           (affects a single actor)
>
>   * `all-for-one`           (affects all supervised actors)
>
> **retries** _(optional)_            - The maximum number of retries. _(default **1**)_
>
> **within** _(optional)_             - The duration within which the maximum number of retries
>                                       is allowed before stopping the actor(s).
>                                       _(default **Infinite**)_
>
> **log** _(optional)_                - Whether or not to log supervisory actions.
>                                       _(default **true**)_
>
> **decider** _(optional)_            - Decider configuration with any of the following keys,
>                                       which are **case-sensitive**:
>
>   * `resume`                (resume the actor as if nothing happened)
>
>   * `restart`               (restart the actor or actors)
>
>   * `escalate`              (escalate to the `/user` supervisor)
>
>   * `stop`                  (stop the actor or actors)


##### Decider

All 4 decider keys are optional, but for each the value may be a
string or a list of strings.

If a single string, it must be a FQCN of a `Throwable` subclass
or the special string `"*"`, meaning all throwables.

If a list of strings, each must be a FQCN of a `Throwable` subclass.

An example:

    decider = {
      resume: "java.lang.ArithmeticException"
      restart: ["java.lang.IllegalArgumentException", "java.lang.IllegalStateException"]
      stop: "*"
    }

This will resume on ArithmeticException, restart on IllegalArgument or IllegalState,
and stop on any other type of throwable.

It works because the actions are always tested against the actual throwable
in the following order:

> resume, restart, escalate, stop

Without a decider, or if no rule matches an actual throwable, `escalate` will
be used to send it up to the `/user` supervisor.


### Use

Simply use `akkaboot.Main` as your program's main entry point.

Until the actor system is started, any problems will be reported to standard error.
Once the actor system is started, Akka logging is used to provide information & diagnostics.


### Testing

To run the tests, from sbt issue the following command:

    test:run -Dconfig.file=./src/test/resources/application.conf

All configured actors should start with no problems. Control-C to stop.


### License

MIT.
