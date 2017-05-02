package test;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import scala.util.Try;
import scala.util.Success;
import scala.util.Failure;

public class Static extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public static Try<ActorRef> create(final ActorRefFactory factory, final akkaboot.Boot.ActorOptions options) {
    try {
      return new Success<ActorRef>(factory.actorOf(Props.create(Static.class)));
    }
    catch (Exception e) {
      return new Failure<ActorRef>(e);
    }
  }


  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Config.class, config -> {
        log.info("Static running with value {}", config.getBoolean("value"));
      })
      .build();
  }
}