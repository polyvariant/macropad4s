package org.polyvariant.macropad4s

import cats.effect.IO
import cats.effect.Resource
import fs2.Stream
import fs2.io.readInputStream

import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

object InputReader {

  val EventSize = InputEvent.EventSize

  def openDevice(path: Path): Resource[IO, (FileDescriptor, Stream[IO, InputEvent])] =
    for {
      fis <- Resource.fromAutoCloseable(IO(new FileInputStream(path.toFile)))
      fd = fis.getFD
    } yield {
      val raw: Stream[IO, Byte] =
        readInputStream(IO.pure(fis), chunkSize = EventSize, closeAfterUse = false)

      val events: Stream[IO, InputEvent] =
        raw.chunkN(EventSize, allowFewer = false).map { chunk =>
          InputEvent.fromBytes(chunk.toArray)
        }

      (fd, events)
    }

}