//> using platform native
//> using dep co.fs2::fs2-io::3.13.0-M7

package org.polyvariant.macropad4s

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import fs2.Chunk
import fs2.Stream
import fs2.io.file.{Files, FileHandle}
import fs2.text

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import scala.scalanative.unsafe.*
import scala.concurrent.duration.*
import fs2.io.writeOutputStream

import KeyCodes.*

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {

    println("Running keyboard grabber!")

    // Find with VENDOR="514c"; PRODUCT="8851"; for ev in /dev/input/event*; do udevadm info -q property -n "$ev" | grep -q "ID_VENDOR_ID=${VENDOR}" && udevadm info -q property -n "$ev" | grep -q "ID_MODEL_ID=${PRODUCT}" && echo "$ev"; done
    val path = "/dev/input/event11"

    Macropad.make(path).exclusiveEventsStream.use { stream =>
      stream
        .groupWithin(6, 50.millis)
        .flatMap(handleChunk)
        .compile
        .drain
        .as(ExitCode.Success)

    }

  }

  def handleChunk(chunk: Chunk[InputEvent]) = {
    val events = chunk.toList

    if (
      events
        .exists(ev => ev.eventType == EV_KEY && ev.code == 2 && ev.value == 1)
    ) {
      // left turn → volume up
      val ev = events
        .find(ev => ev.eventType == EV_KEY && ev.code == 2 && ev.value == 1)
        .head
      println(s"Mapping $ev to VOL UP")
      val press = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEUP, 1)
      val sync1 = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
      val release = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEUP, 0)
      val sync2 = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
      Stream(press, sync1, release, sync2)
        .flatMap(ev => Stream.chunk(Chunk.array(InputEvent.toBytes(ev))))

    } else if (
      events
        .exists(ev => ev.eventType == EV_KEY && ev.code == 4 && ev.value == 1)
    ) {
      // right turn → volume down
      val ev = events
        .find(ev => ev.eventType == EV_KEY && ev.code == 4 && ev.value == 1)
        .head
      println(s"Mapping $ev to VOL DOWN")
      val press = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEDOWN, 1)
      val sync1 = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
      val release = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEDOWN, 0)
      val sync2 = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
      Stream(press, sync1, release, sync2)
        .flatMap(ev => Stream.chunk(Chunk.array(InputEvent.toBytes(ev))))

    } else Stream.empty
  }

}
