package test

import akka.actor.{Actor, ActorLogging, Props}
import akkaboot.ConfigHelp
import akkaboot.Boot.ActorGenerator
import com.typesafe.config.Config
import scala.util.Try

class PlainActor extends Actor with ActorLogging {
  log.info("PlainActor running")
  def receive = Actor.ignoringBehavior
}

object PlainActor {
  def create(): ActorGenerator = {
    case (factory, actorOptions) => Try(factory.actorOf(Props[PlainActor]))
  }
}


class ByParam(config: Config) extends Actor with ActorLogging {
  log.info("ByParam running with value {}", config.getBoolean("value"))
  def receive = Actor.ignoringBehavior
}

object ByParam {
  def create(): ActorGenerator = {
    case (factory, actorOptions) =>
      Try(factory.actorOf(Props(classOf[ByParam], actorOptions.configuration)))
  }
}


class ByMessage extends Actor with ActorLogging {
  log.info("ByMessage running")
  def receive = {
    case config: Config =>
      log.info("received config with value {}", config.getBoolean("value"))
  }
}

object ByMessage {
  def create(): ActorGenerator = {
    case (factory, actorOptions) =>
      Try(factory.actorOf(Props[ByMessage]))
  }
}


class EmptyParamConfig(config: Config) extends Actor with ActorLogging with ConfigHelp {
  log.info("EmptyParamConfig running with value {}", config.booleanWithDefault("value", false))
  def receive = Actor.ignoringBehavior
}

object EmptyParamConfig {
  def create(): ActorGenerator = {
    case (factory, actorOptions) =>
      Try(factory.actorOf(Props(classOf[EmptyParamConfig], actorOptions.configuration)))
  }
}


class EmptyMessageConfig extends Actor with ActorLogging with ConfigHelp {
  log.info("EmptyMessageConfig running")

  def receive = {
    case config: Config =>
      log.info("received configuration with value {}", config.booleanWithDefault("value", false))
  }
}

object EmptyMessageConfig {
  def create(): ActorGenerator = {
    case (factory, actorOptions) =>
      Try(factory.actorOf(Props[EmptyMessageConfig]))
  }
}