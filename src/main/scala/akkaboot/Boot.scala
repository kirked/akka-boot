/*-----------------------------------------------------------------------------
 * MIT License
 * 
 * Copyright (c) 2017 Doug Kirk
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *---------------------------------------------------------------------------*/

package akkaboot

import akka.actor.{Actor,
                   ActorLogging,
                   ActorRef,
                   ActorRefFactory,
                   ExtendedActorSystem,
                   Props}
import com.typesafe.config.{Config, ConfigFactory}
import java.net.URI
import scala.util.{Try, Success, Failure}

/**
 * Starts the top-level actors in the application
 * (specified in the boot configuration), then dies.
 */
class Boot(args: Array[String], bootConfig: Config)
    extends Actor
    with ActorLogging
    with ConfigHelp {

    import Boot._

    startupBanner()

    val reflector = context.system.asInstanceOf[ExtendedActorSystem].dynamicAccess
    val abortOnFailure = bootConfig.booleanWithDefault("abort-on-failure", true)
    val actors: List[Config] = bootConfig.configList("actors")

    val supervisors: Map[String, ActorRef] = {
      val supervisorConfigs = bootConfig.configList("supervisors")
      log.info("{} supervisors configured", supervisorConfigs.size)
      supervisorConfigs.map(createSupervisor).toMap
    }

    log.info("{} actors configured", actors.size)

    for (config <- actors) self ! parseActorConfig(config)

    var started = 0
    var skipped = 0
    var aborted = false


  def receive = {
    case ActorOptions(name, _, _, _, _, false) =>
      log.info("{} skipped (disabled)", name)
      skipped = skipped + 1
      checkDone

    case options @ ActorOptions(name, _, _, _, _, true) =>
      if (aborted) skipped = skipped + 1
      else {
        log.info("{} starting", name)
        if (startActor(options)) started = started + 1
      }
      checkDone
    
    case InvalidConfig(config, msg, err) =>
      err match {
        case Some(err) =>
          log.error(err, msg)
        
        case None =>
          log.error(msg)
      }
      skipped = skipped + 1
      if (abortOnFailure) abort
      else checkDone

    case (supervisedActor: ActorRef, actorOptions: ActorOptions) =>
      log.debug("{} started", actorOptions.name)
      sendConfigAsMessage(supervisedActor, actorOptions)
      started = started + 1
      checkDone
  }


  override def preRestart(reason: Throwable, msg: Option[Any]): Unit = {
    msg match {
      case Some(msg) => log.error(reason, "error during boot while starting {}", msg)
      case None      => log.error(reason, "error during boot")
    }
    abort
  }


  def startActor(actorOptions: ActorOptions): Boolean = {
    actorOptions.generator(context.system, actorOptions) match {
      case Success(Some(actor)) =>
        log.debug("{} started", actorOptions.name)
        sendConfigAsMessage(actor, actorOptions)
        true

      case Success(None) =>
        // the case for supervised actors
        false

      case Failure(err) =>
        log.error(err, "startup of {} failed", actorOptions.name)
        if (abortOnFailure) abort
        false
    }
  }


  def sendConfigAsMessage(actor: ActorRef, actorOptions: ActorOptions): Unit = {
    if (actorOptions.configAsMessage) {
      if (actorOptions.configuration.isEmpty) {
        log.warning("empty actor configuration for {} when config-as-message is true", actorOptions.name)
        log.warning("+- sending empty configuration to {}", actor)
      }
      actor ! actorOptions.configuration
    }
  }


  def checkDone(): Unit = if (started + skipped >= actors.size) {
    if (!aborted) startupComplete("complete")
    context stop self
  }


  def abort(): Unit = {
    aborted = true
    startupComplete("ABORTING")
    context.system.terminate
  }


  def startupBanner(): Unit = {
    log.info("=================================================================")
    log.info("initialising actor system {}", context.system.name)
  }


  def startupComplete(status: String): Unit = {
    log.info("startup {}", status)
    log.info("=================================================================")
  }


  def createSupervisor(config: Config): (String, ActorRef) = {
    val name = config.getString("name")
    val actor = context.system.actorOf(GenericSupervisor.props(config), name = name)
    (name, actor)
  }


  /** Parse an actor specification from a configuration. */
  def parseActorConfig(config: Config): ActorSpec = {
    val name = config.getString("name")
    val enabled = config.booleanWithDefault("enabled", true)
    val actorConfig = config.configOption("config").getOrElse(ConfigFactory.empty)
    val configAsParam = config.booleanWithDefault("config-as-parameter", false)
    val configAsMessage = config.booleanWithDefault("config-as-message", false)

    Try(new URI(config getString "generator")).flatMap(parseGenerator) match {
      case Success(gen) =>
        ActorOptions(name, gen, actorConfig, configAsParam, configAsMessage, enabled)

      case Failure(err) =>
        InvalidConfig(config, "couldn't process actor configuration", Some(err))
    }
  }


  /** Parse an actor generator URI. */
  def parseGenerator(uri: URI): Try[ActorGenerator] = uri match {
    case ClassGenerator(generator)      => Success(generator)
    case FactoryGenerator(generator)    => Success(generator)
    case SupervisorGenerator(generator) => Success(generator)
    case _ => Failure(new IllegalArgumentException(s"invalid actor generator URI: [$uri]"))
  }


  /** Parse an actor class specification: "class:fqcn" */
  final object ClassGenerator {
    def unapply(uri: URI): Option[ActorGenerator] = uri.getScheme match {
      case "class" =>
        if (uri.getAuthority ne null) None
        else if (uri.getPath ne null) None
        else if (uri.getFragment ne null) None
        else if (uri.getQuery ne null) None
        else if (uri.getSchemeSpecificPart eq null) None
        else {
          val fqcn = uri.getSchemeSpecificPart
          reflector.getClassFor[Actor](fqcn) match {
            case Success(clazz) => Success(gen(clazz))
            case Failure(err) =>
              log.error(err, "can't load actor class [{}] for URI [{}]", fqcn, uri)
              Failure(err)
          }
        }.toOption

      case _ => None
    }


    def propsForClass[A <: Actor](clazz: Class[A], actorOptions: ActorOptions): Props = {
      if (actorOptions.configAsParam) {
        if (actorOptions.configuration.isEmpty) {
          log.warning("no actor configuration for {} when config-as-param is true",
              actorOptions.name)
          log.warning("+- providing empty configuration")
        }
        Props(clazz, actorOptions.configuration)
      }
      else Props(clazz)
    }


    private def gen[A <: Actor](clazz: Class[A]): ActorGenerator = {
      case (factory, actorOptions) =>
        Try {
          val props = propsForClass(clazz, actorOptions)
          val actor = factory.actorOf(props, name = actorOptions.name)
          log.info("created actor {}", actorOptions.name)
          Some(actor)
        }
    }
  }


  /** Parse an actor factory specification: "factory:fqcn/method" */
  final object FactoryGenerator {
    import java.lang.reflect.{Method, Modifier}

      // parse URI format "factory:fqcn/method"
    def unapply(uri: URI): Option[ActorGenerator] = uri.getScheme match {
      case "factory" =>
        if (uri.getAuthority ne null) None
        else if (uri.getPath ne null) None
        else if (uri.getFragment ne null) None
        else if (uri.getQuery ne null) None
        else if (uri.getSchemeSpecificPart eq null) None
        else {
          val Array(fqcn, methodName) = uri.getSchemeSpecificPart.split('/')
          reflector.getObjectFor[AnyRef](fqcn) match {
            case Success(scalaObject) => scalaFactory(scalaObject, fqcn, methodName, uri)
            case Failure(err)         => javaFactory(fqcn, methodName, uri)
          }
        }.toOption

      case _ => None
    }


    private def scalaFactory(scalaObject: AnyRef, fqcn: String, methodName: String, uri: URI): Try[ActorGenerator] = {
      Try(scalaObject.getClass.getMethod(methodName)) map {
        _.invoke(scalaObject)
      } match {
        case Success(f: Function2[_, _, _]) =>
          Success(f.asInstanceOf[ActorGenerator])

        case Success(unexpected) =>
          val err = new IllegalStateException(s"$fqcn.$methodName should return an ActorGenerator for URI [$uri]")
          log.error(err.getMessage)
          Failure(err)

        case Failure(err) =>
          log.error(err, "couldn't find Scala object method [{}] for URI [{}]", methodName, uri)
          Failure(err)
      }
    }


    private def javaFactory(fqcn: String, methodName: String, uri: URI): Try[ActorGenerator] = {
      reflector.getClassFor[AnyRef](fqcn) match {
        case Success(clazz) =>
          Try(clazz.getMethod(methodName, classOf[ActorRefFactory], classOf[ActorOptions])) match {
            case Success(method) =>
              val mods = method.getModifiers
              if (Modifier.isStatic(mods) && Modifier.isPublic(mods)) {
                Success(staticGenerator(method))
              }
              else {
                val err = new IllegalStateException(s"couldn't find Java public static method $methodName for URI [$uri]")
                log.error(err.getMessage)
                Failure(err)
              }

            case Failure(err) =>
              log.error("couldn't find method {} for URI [{}]; tried Scala object and Java static",
                  methodName, uri)
              Failure(err)
          }

        case Failure(err) =>
          log.error(err, "couldn't load class {} for URI [{}]", fqcn, uri)
          Failure(err)
      }
    }


    private def staticGenerator(method: Method): ActorGenerator = {
      case (factory, actorOptions) =>
        Try(
          Some(method.invoke(null, factory, actorOptions).asInstanceOf[ActorRef])
        )
    }
  }


  /** Parse a supervised actor specification: "supervisor:name/fqcn" */
  final object SupervisorGenerator {
    def unapply(uri: URI): Option[ActorGenerator] = uri.getScheme match {
      case "supervisor" =>
        if (uri.getAuthority ne null) None
        else if (uri.getPath ne null) None
        else if (uri.getFragment ne null) None
        else if (uri.getQuery ne null) None
        else if (uri.getSchemeSpecificPart eq null) None
        else {
          val Array(supervisor, fqcn) = uri.getSchemeSpecificPart.split("/")
          reflector.getClassFor[Actor](fqcn) match {
            case Success(clazz) if (supervisors contains supervisor) =>
              Success(gen(supervisor, clazz))

            case Success(clazz) =>
              log.error(s"no supervisor named [$supervisor] for URI [$uri]")
              Failure(new IllegalArgumentException)

            case Failure(err) =>
              log.error(err, "can't load actor class [{}] for URI [{}]", fqcn, uri)
              Failure(err)
          }
        }.toOption

      case _ => None
    }


    private def gen[A <: Actor](supervisor: String, clazz: Class[A]): ActorGenerator = {
      case (_factory, actorOptions) =>
        val props = ClassGenerator.propsForClass(clazz, actorOptions)
        supervisors get supervisor match {
          case Some(supervisorActor) =>
            supervisorActor ! (props, actorOptions)
            Success(None)

          case None =>
            Failure(new IllegalStateException(s"no supervisor named [$supervisor] for actor named [${actorOptions.name}]"))
        }
    }
  }
}


object Boot {
  def props(args: Array[String], config: Config) = Props(new Boot(args, config))


  type ActorGenerator = (ActorRefFactory, ActorOptions) => Try[Option[ActorRef]]

  sealed trait ActorSpec

  final case class InvalidConfig(
      config: Config,
      msg: String,
      err: Option[Throwable] = None) extends ActorSpec

  final case class ActorOptions(
      name: String,
      generator: ActorGenerator,
      configuration: Config,
      configAsParam: Boolean,
      configAsMessage: Boolean,
      enabled: Boolean) extends ActorSpec
}