//> using dep com.lihaoyi::os-lib::0.11.4
//> using dep io.github.zainab-ali::aquascape::0.4.1

package org.polyvariant.macropad4s

import InputEventCodes.*

import aquascape.*
import cats.effect.*
import fs2.*

object App extends AquascapeApp {

  def name: String = "aquascapeFrame"

  def stream(using Scape[IO]): IO[Unit] = {
    // Find with VENDOR="514c"; PRODUCT="8851"; for ev in /dev/input/event*; do udevadm info -q property -n "$ev" | grep -q "ID_VENDOR_ID=${VENDOR}" && udevadm info -q property -n "$ev" | grep -q "ID_MODEL_ID=${PRODUCT}" && echo "$ev"; done
    val path = "/dev/input/event23"

    Macropad
      .make(path)
      .grabKeyboardEventsStream
      .use { stream =>
        println("ready!")
        stream
          // .evalTap(event => IO.println(s"Found event: $event"))
          .stage("Macropad events")
          .evalMap(handleEvent)
          .stage("Process event")
          .take(9)
          .compile
          .drain
          .compileStage("Result stream")
          .void
      }

  }

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
