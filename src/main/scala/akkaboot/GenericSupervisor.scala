package akkaboot

import akka.actor.{Actor,
                   ActorLogging,
                   ActorRef,
                   AllForOneStrategy,
                   OneForOneStrategy,
                   Props,
                   SupervisorStrategy}
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.util.Try

class GenericSupervisor(config: Config)
    extends Actor
    with ActorLogging
    with ConfigHelp {

    import SupervisorStrategy._

    val maxNrOfRetries = config.intOption("retries").getOrElse(1)
    val withinTimeRange = config.durationOption("within").getOrElse(Duration.Inf)
    val loggingEnabled = config.booleanWithDefault("log", true)
    val decider: PartialFunction[Throwable, Directive] = {
      val decisions = List(Resume, Restart, Escalate, Stop) map exceptionsForDirective

      {
        case t: Throwable =>
          val noDecision = Option.empty[Directive]
          val decision = decisions.foldLeft(noDecision) {
            case (result @ Some(_), _) => result

            case (None, (directive, classes)) =>
              classes.foldLeft(noDecision) {
                case (result @ Some(_), _) => result

                case (None, klass) =>
                  if (klass.isAssignableFrom(t.getClass)) Some(directive)
                  else noDecision
              }
          }
          decision getOrElse Escalate
      }
    }


  def receive = {
    case props: Props =>
      sender ! context.actorOf(props)

    case (props: Props, actorOptions: Boot.ActorOptions) =>
      sender ! (context.actorOf(props, name = actorOptions.name), actorOptions)
  }


  override def supervisorStrategy = config.getString("strategy") match {
    case "one-for-one" =>
      OneForOneStrategy(maxNrOfRetries, withinTimeRange, loggingEnabled)(decider)

    case "all-for-one" =>
      AllForOneStrategy(maxNrOfRetries, withinTimeRange, loggingEnabled)(decider)
  }


  def exceptionsForDirective[T <: Throwable](directive: Directive): (Directive, List[Class[T]]) = {
    val key = s"decider.${directive.toString.toLowerCase}"
    val exceptionNames: List[String] = Try {
      config.stringList(key)
    } orElse Try {
      if (config.hasPath(key)) List(config.getString(key)) else List.empty
    } getOrElse List.empty

    val exceptions = exceptionNames.map { name =>
      Try {
        if (name == "*") classOf[Throwable].asInstanceOf[Class[T]]
        else Class.forName(name).asInstanceOf[Class[T]]
      }.toOption
    }.flatten

    (directive -> exceptions)
  }
}


object GenericSupervisor {
  def props(config: Config) = Props(new GenericSupervisor(config))
}