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
    log.info("{} root actors configured", actors.size)

    for (config <- actors) self ! parseConfig(config)

    var started = 0
    var skipped = 0


  def receive = {
    case ActorOptions(name, _, _, _, _, false) =>
      log.debug("{} skipped (disabled)", name)
      skipped = skipped + 1
      checkDone

    case options @ ActorOptions(name, generator, config, _, configAsMessage, true) =>
      log.info("{} starting", name)
      startActor(options)
      started = started + 1
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
  }


  override def preRestart(reason: Throwable, msg: Option[Any]): Unit = {
    msg match {
      case Some(msg) => log.error(reason, "error during boot while starting {}", msg)
      case None      => log.error(reason, "error during boot")
    }
    abort
  }


  def startActor(actorOptions: ActorOptions): Unit = {
    actorOptions.generator(context.system, actorOptions) match {
      case Success(actor) =>
        log.debug("{} started", actorOptions.name)
        (actorOptions.configAsMessage, actorOptions.configuration) match {
          case (false, _)           => ()
          case (true, Some(config)) => actor ! config
          case (true, None) =>
            log.warning("no actor configuration for {} when config-as-message is true", actorOptions.name)
            log.warning("+- sending empty configuration to {}", actor)
            actor ! ConfigFactory.empty
        }

        case Failure(err) =>
          log.error(err, "startup of {} failed", actorOptions.name)
          if (abortOnFailure) abort
    }
  }

  def checkDone(): Unit = if (started + skipped >= actors.size) {
    startupComplete("complete")
    context stop self
  }


  def abort(): Unit = {
    startupComplete("ABORTING")
    context.system.terminate
  }


  def startupBanner(): Unit = {
    log.info("=================================================================")
    log.info("initialising {}", context.system.name)
  }


  def startupComplete(status: String): Unit = {
    log.info("startup {}", status)
    log.info("=================================================================")
  }


  /** Parse an actor specification from a configuration. */
  def parseConfig(config: Config): ActorSpec = {
    val name = config.getString("name")
    val enabled = config.booleanWithDefault("enabled", true)
    val actorConfig = config.configOption("config")
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
    case ClassGenerator(generator)    => Success(generator)
    case FactoryGenerator(generator)  => Success(generator)
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


    private def gen[A <: Actor](clazz: Class[A]): ActorGenerator = {
      case (factory, actorOptions) =>
        Try {
          val props = (actorOptions.configAsParam, actorOptions.configuration) match {
            case (false, _)           => Props(clazz)
            case (true, Some(config)) => Props(clazz, config)
            
            case (true, None) =>
              log.warning("no actor configuration for {} when config-as-param is true", actorOptions.name)
              log.warning("+- providing empty configuration")
              Props(clazz, ConfigFactory.empty)
          }
          factory.actorOf(props, name = actorOptions.name)
        }
    }
  }


  /** Parse an actor factory specification: "factory:fqcn/method" */
  final object FactoryGenerator {
    import java.lang.reflect.Method

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
          reflector.getClassFor[AnyRef](fqcn) match {
            case Success(clazz) =>
              Try(clazz.getMethod(methodName, classOf[ActorRefFactory], classOf[ActorOptions])) match {
                case Success(method) =>
                  val instance: AnyRef = reflector.getObjectFor[AnyRef](fqcn) match {
                    case Success(scalaObject) => scalaObject
                    case Failure(err) =>
                      log.warning("method [{}.{}] will be invoked statically for URI [{}]",
                        fqcn, methodName, uri)
                      null
                  }
                  Success(gen(instance, method))

                case Failure(err) =>
                  log.error(err, "couldn't find method [{}] for URI [{}]", methodName, uri)
                  Failure(err)
              }

            case Failure(err) =>
              log.error(err, "couldn't load factory class [{}] for URI [{}]", fqcn, uri)
              Failure(err)
          }
        }.toOption

      case _ => None
    }


    private def gen(instance: AnyRef, method: Method): ActorGenerator = {
      case (factory, actorOptions) =>
        Try(
          method.invoke(instance, Array(factory, actorOptions)).asInstanceOf[ActorRef]
        )
    }
  }
}


object Boot {
  def props(args: Array[String], config: Config) = Props(new Boot(args, config))


  type ActorGenerator = (ActorRefFactory, ActorOptions) => Try[ActorRef]

  sealed trait ActorSpec

  final case class InvalidConfig(
      config: Config,
      msg: String,
      err: Option[Throwable] = None) extends ActorSpec

  final case class ActorOptions(
      name: String,
      generator: ActorGenerator,
      configuration: Option[Config],
      configAsParam: Boolean,
      configAsMessage: Boolean,
      enabled: Boolean) extends ActorSpec
}