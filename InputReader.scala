package org.polyvariant.macropad4s

import cats.effect.{IO, Resource}
import fs2.Stream
import fs2.io.readInputStream

import java.io.{FileDescriptor, FileInputStream}
import java.nio.file.Path
import org.polyvariant.macropad4s.InputEvent.EventSize

object InputReader {

  /** Open a device file and return its FileDescriptor along with a stream of InputEvents. */
  def openDevice(path: Path): Resource[IO, (FileDescriptor, Stream[IO, InputEvent])] =
    for {
      fis <- fileInputStream(path)
      fd   = fis.getFD
    } yield (fd, inputEvents(fis))

  private def fileInputStream(path: Path) =
    Resource.fromAutoCloseable(IO(new FileInputStream(path.toFile)))

  private def inputEvents(fis: FileInputStream) =
    rawBytes(fis)
      .chunkN(EventSize, allowFewer = false)
      .map(chunk => InputEvent.fromBytes(chunk.toArray))

  private def rawBytes(fis: FileInputStream) =
    readInputStream(IO.pure(fis), chunkSize = EventSize, closeAfterUse = false)
}
