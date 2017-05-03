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
        system.registerOnTermination[Unit] {
          sys.exit(0)
        }
      }

      system.actorOf(Boot.props(args, bootConfig), "BOOT")
    }
  }
}