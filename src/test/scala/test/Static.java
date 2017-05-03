package test;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;

public class Static extends AbstractActor {
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public static ActorRef create(final ActorRefFactory factory, final akkaboot.Boot.ActorOptions options) {
    return factory.actorOf(Props.create(Static.class));
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