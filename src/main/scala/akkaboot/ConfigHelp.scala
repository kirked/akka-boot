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

import com.typesafe.config.Config
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

trait ConfigHelp {
  implicit class ConfigHelper(config: Config) {
    import scala.collection.JavaConverters._

    def booleanWithDefault(path: String, defaultValue: Boolean): Boolean =
        if (config.hasPath(path)) config.getBoolean(path) else defaultValue

    def intOption(path: String): Option[Int] =
        if (config.hasPath(path)) Some(config.getInt(path)) else None

    def stringWithDefault(path: String, defaultValue: String): String =
        if (config.hasPath(path)) config.getString(path) else defaultValue

    def stringOption(path: String): Option[String] =
        if (config.hasPath(path)) Some(config.getString(path)) else None

    def stringList(path: String): List[String] =
        if (config.hasPath(path)) config.getStringList(path).asScala.toList else List.empty

    def configOption(path: String): Option[Config] =
        if (config.hasPath(path)) Some(config.getConfig(path)) else None
        
    def configList(path: String): List[Config] =
        if (config.hasPath(path)) config.getConfigList(path).asScala.toList else List.empty

    def durationOption(path: String): Option[FiniteDuration] = {
      if (config.hasPath(path)) {
        Try {
          val duration = Duration(config.getString(path))
          FiniteDuration(duration.length, duration.unit)
        }.toOption
      }
      else None
    }
  }
}