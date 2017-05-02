/*-----------------------------------------------------------------------------
 * Copyright 2017 Doug Kirk
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *---------------------------------------------------------------------------*/

package akkaboot

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object Main extends App with ConfigHelp {
  val config = ConfigFactory.load

  if (!config.hasPath("boot") || config.getConfig("boot").isEmpty) {
    Console.err.println("The required configuration object 'boot' is missing or empty.\n")
    Console.err.println("This provides the boot options: 'name' and 'actors', which")
    Console.err.println("are the actor system name and the list of actors to start.")
    sys.exit(1)
  }
  else {
    val bootConfig = config.getConfig("boot")

    if (!bootConfig.hasPath("name")) {
      Console.err.println("The required configuration string 'boot.name' is missing.")
      Console.err.println("This provides the name for the actor system.")
      sys.exit(1)
    }
    else if (!bootConfig.hasPath("actors")) {
      Console.err.println("The required configuration list 'boot.actors' is missing.")
      Console.err.println("This provides the information needed to start the initial actors.")
      sys.exit(1)
    }
    else {
      val name = bootConfig.getString("name")
      val system = ActorSystem(name, config.withoutPath("boot"))

      if (bootConfig.booleanWithDefault("exit-on-termination", true)) {
        system.registerOnTermination {
          sys.exit(0)
        }
      }

      system.actorOf(Boot.props(args, bootConfig), "BOOT")
    }
  }
}