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
        for {
          (fd, stream) <- InputReader.openDevice(Paths.get(path))
          _            <- Grabber.resource(fd)
        } yield stream
      }
    }
}
