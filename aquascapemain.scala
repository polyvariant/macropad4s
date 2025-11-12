//> using dep com.lihaoyi::os-lib::0.11.4
//> using dep io.github.zainab-ali::aquascape::0.4.1

package org.polyvariant.macropad4s

import InputEventCodes.*

import aquascape.*
import cats.effect.*
import fs2.*
import scala.concurrent.duration.*
import cats.Show
import cats.syntax.all.*

object App extends AquascapeApp {

  def name: String = "aquascapeFrame"

  def stream(using Scape[IO]): IO[Unit] = {
    // Find with VENDOR="514c"; PRODUCT="8851"; for ev in /dev/input/event*; do udevadm info -q property -n "$ev" | grep -q "ID_VENDOR_ID=${VENDOR}" && udevadm info -q property -n "$ev" | grep -q "ID_MODEL_ID=${PRODUCT}" && echo "$ev"; done
    val path = "/dev/input/event7"

    Macropad
      .make(path)
      .grabKeyboardEventsStream
      .use { stream =>
        println("ready!")
        stream
          .stage("Macropad events", "upstream")
          .groupWithin(Int.MaxValue, 1.second)
          .fork("root", "upstream")
          .evalMap(handleEvents)
          .stage("Process events")
          .take(2)
          .compile
          .drain
          .compileStage("Result stream")
          .void
      }

  }

  given Show[Chunk[InputEvent]] = _ => "[events...]"
  // given Show[Chunk[InputEvent]] = _.toList.map(_.show).mkString("[", ", ", "]")

  private def handleEvents(evs: Chunk[InputEvent])(using Scape[IO]): IO[String] = {
    println(evs)
    if(evs.toList.exists(isVolUp)) volumeUp.as("Volume up!")
    else if(evs.toList.exists(isVolDown)) volumeDown.as("Volume down!")
    else IO.unit.as("Ignored")
  }

  private def isVolUp(ev: InputEvent) = ev.isKeyPress && ev.code == KEY_3
  private def isVolDown(ev: InputEvent) = ev.isKeyPress && ev.code == KEY_1

  private def handleEvent(ev: InputEvent)(using Scape[IO]): IO[String] = {
    if (ev.isKeyPress && ev.code == KEY_1) {
      IO.println(s"Got $ev turning vol down") *>
        volumeDown.as("Volume down!")
    } else if (ev.isKeyPress && ev.code == KEY_3) {
      IO.println(s"Got $ev turning vol up") *>
        volumeUp.as("Volume up!")
    } else IO.unit.as("Ignored")
  }

  private val volumeUp =
    runCommand(os.Shellable(List("amixer", "sset", "Master", "5%+")))

  private val volumeDown =
    runCommand(os.Shellable(List("amixer", "sset", "Master", "5%-")))

  private def runCommand(cmd: os.Shellable): IO[Unit] =
    IO.delay(os.call(cmd)).void

}
