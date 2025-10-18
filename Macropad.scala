package org.polyvariant.macropad4s

import cats.effect.IO
import cats.effect.Resource
import fs2.Stream
import java.nio.file.Paths

trait Macropad {
  def grabKeyboardEventsStream: Resource[IO, Stream[IO, InputEvent]]
}

object Macropad {

  // Find path with VENDOR="514c"; PRODUCT="8851"; for ev in /dev/input/event*; do udevadm info -q property -n "$ev" | grep -q "ID_VENDOR_ID=${VENDOR}" && udevadm info -q property -n "$ev" | grep -q "ID_MODEL_ID=${PRODUCT}" && echo "$ev"; done
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
