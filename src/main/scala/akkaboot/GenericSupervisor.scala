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

    log.info("supervisor {} starting with maxRetries={}, timeRange={}, logging={}",
        config.getString("name"), maxNrOfRetries, withinTimeRange, loggingEnabled)


  def receive = {
    case (props: Props, actorOptions: Boot.ActorOptions) =>
      log.info("{} starting", actorOptions.name)
      sender ! (context.actorOf(props, name = actorOptions.name), actorOptions)

    case msg =>
      log.error("received unexpected message {}", msg)
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