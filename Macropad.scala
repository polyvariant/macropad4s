package org.polyvariant.macropad4s

import cats.effect.IO
import cats.effect.Resource
import fs2.Stream
import java.nio.file.Paths

trait Macropad {
  def grabKeyboardEventsStream: Resource[IO, Stream[IO, InputEvent]]
}

object Macropad {

  def make(path: String): Macropad = 
    new Macropad {
      def grabKeyboardEventsStream: Resource[IO, Stream[IO, InputEvent]] = {
        InputReader.openDevice(Paths.get(path)).flatMap { (fd, stream) => 
          Resource
            .make(Grabber.grab(fd).as(stream))(_ => Grabber.release(fd).void)
        }
      }
    }
}
