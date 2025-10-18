//> using platform native
//> using dep co.fs2::fs2-io::3.13.0-M7
//> using dep com.lihaoyi::os-lib::0.11.4

package org.polyvariant.macropad4s

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import InputEventCodes.*

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    // Find with VENDOR="514c"; PRODUCT="8851"; for ev in /dev/input/event*; do udevadm info -q property -n "$ev" | grep -q "ID_VENDOR_ID=${VENDOR}" && udevadm info -q property -n "$ev" | grep -q "ID_MODEL_ID=${PRODUCT}" && echo "$ev"; done
    val defaultPath = "/dev/input/event11"
    val path = args.headOption.getOrElse(defaultPath)

    IO.println(s"Running keyboard grabber! $args") *>
      Macropad
        .make(path)
        .grabKeyboardEventsStream
        .use { stream =>
          stream
            .evalTap(event => IO.println(s"Found event: $event"))
            .evalTap(handleEvent)
            .compile
            .drain
        }
        .as(ExitCode.Success)

  }

  private def handleEvent(ev: InputEvent): IO[Unit] = {
    if (ev.isKeyPress && ev.code == KEY_1) {
      IO.println(s"Got $ev turning vol down") *>
        volumeDown
    } else if (ev.isKeyPress && ev.code == KEY_3) {
      IO.println(s"Got $ev turning vol up") *>
        volumeUp
    } else IO.unit
  }

  private val volumeUp =
    runCommand(os.Shellable(List("amixer", "sset", "Master", "5%+")))

  private val volumeDown =
    runCommand(os.Shellable(List("amixer", "sset", "Master", "5%-")))

  private def runCommand(cmd: os.Shellable): IO[Unit] =
    IO.delay(os.call(cmd)).void

}
