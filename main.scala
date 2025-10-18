//> using platform native
//> using dep co.fs2::fs2-io::3.13.0-M7

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

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {

    println("Running keyboard grabber!")

    // Find with VENDOR="514c"; PRODUCT="8851"; for ev in /dev/input/event*; do udevadm info -q property -n "$ev" | grep -q "ID_VENDOR_ID=${VENDOR}" && udevadm info -q property -n "$ev" | grep -q "ID_MODEL_ID=${PRODUCT}" && echo "$ev"; done
    val path = "/dev/input/event11"

    val res = for  {
      uinput <- UInput.init().toResource
      device <- InputReader.openDevice(Paths.get(path))
    } yield (uinput, device)
    res.use { case (uinputFile, (fd, stream)) => 

      val uinput: fs2.Pipe[IO, Byte, Unit] =
        writeOutputStream(IO(uinputFile), closeAfterUse = false)

      val mainLoop = 
        stream
          .evalTap{ event => 
            IO.println(s"Got event: $event")
          }
          .groupWithin(6, 50.millis)
          .flatMap { chunk =>
            val events = chunk.toList

            if (events.exists(ev => ev.eventType == EV_KEY && ev.code == 2 && ev.value == 1)) {
              // left turn → volume up
              val ev = events.find(ev => ev.eventType == EV_KEY && ev.code == 2 && ev.value == 1).head
              println(s"Mapping $ev to VOL UP")
              val press   = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEUP, 1)
              val sync1   = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
              val release = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEUP, 0)
              val sync2   = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
              Stream(press, sync1, release, sync2)
                .flatMap(ev => Stream.chunk(Chunk.array(InputEvent.toBytes(ev))))

            } else if (events.exists(ev => ev.eventType == EV_KEY && ev.code == 4 && ev.value == 1)) {
              // right turn → volume down
              val ev = events.find(ev => ev.eventType == EV_KEY && ev.code == 4 && ev.value == 1).head
              println(s"Mapping $ev to VOL DOWN")
              val press   = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEDOWN, 1)
              val sync1   = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
              val release = InputEvent(ev.sec, ev.usec, EV_KEY, KEY_VOLUMEDOWN, 0)
              val sync2   = InputEvent(ev.sec, ev.usec, EV_SYN, SYN_REPORT, 0)
              Stream(press, sync1, release, sync2)
                .flatMap(ev => Stream.chunk(Chunk.array(InputEvent.toBytes(ev))))

            } else Stream.empty
          }
          .through(uinput)
          .compile
          .drain

      Grabber.grab(fd) *>
        IO.println("Press enter to interrupt") *>
        IO.race(
          IO.readLine,
          mainLoop
        ) *>
        Grabber.release(fd) *> 
        UInput.destroy(uinputFile).as(ExitCode.Success)
    }
  }

  val EV_KEY = 0x01
  val EV_SYN       = 0x00
  val SYN_REPORT   = 0x00
  val KEY_VOLUMEDOWN = 0x72
  val KEY_VOLUMEUP = 0x73

}

object Grabber {
  import scala.scalanative.posix
  private val EVIOCGRAB: CLongInt = 0x40044590

  def grab(fileDescriptor: FileDescriptor): IO[Int] = IO.delay {
    val grabValue = stackalloc[Byte]()  // Allocate space for a CByte
    !grabValue = 1.toByte  // Set the value of the allocated memory to 1
    posix.sys.ioctl.ioctl(fileDescriptor.value.get, EVIOCGRAB, grabValue)
  }


  def release(fileDescriptor: FileDescriptor): IO[Int] = IO.delay {
    val grabValue = stackalloc[Byte]()  // Allocate space for a CByte
    !grabValue = 0.toByte  // Set the value of the allocated memory to 0
    posix.sys.ioctl.ioctl(fileDescriptor.value.get, EVIOCGRAB, grabValue)
  }
}

extension (fd: FileDescriptor) {
  // File descriptor doesn't give access to the int value
  // but leaks it in toString: FileDescriptor(33, readOnly=true)

  def value: Option[Int] = extractFileDescriptor(fd.toString())

  private def extractFileDescriptor(input: String): Option[Int] = 
    input.split("FileDescriptor\\(").toList match {
      case _ :: tail :: Nil => tail.split(", readOnly").headOption.map(_.toInt)
      case _ => None
    }

}


import fs2.io.readInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

case class InputEvent(sec: Long, usec: Long, eventType: Int, code: Int, value: Int)
object InputEvent {
  val EventSize = 24 // bytes on 64-bit Linux

  def toBytes(ev: InputEvent): Array[Byte] = {
    val buf = ByteBuffer.allocate(EventSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.putLong(ev.sec)
    buf.putLong(ev.usec)
    buf.putShort(ev.eventType.toShort)
    buf.putShort(ev.code.toShort)
    buf.putInt(ev.value)
    buf.array()
  }
}
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
          val buf = ByteBuffer.wrap(chunk.toArray).order(ByteOrder.LITTLE_ENDIAN)
          val sec   = buf.getLong()
          val usec  = buf.getLong()
          val tpe   = buf.getShort() & 0xffff
          val code  = buf.getShort() & 0xffff
          val value = buf.getInt()
          InputEvent(sec, usec, tpe, code, value)
        }

      (fd, events)
    }

}


object UInput {
  import scala.scalanative.posix
  import scala.scalanative.libc

  // from linux/uinput.h
  private val UINPUT_MAX_NAME_SIZE = 80
  private val UI_SET_EVBIT: CLongInt = 0x40045564
  private val UI_SET_KEYBIT: CLongInt = 0x40045565
  private val UI_DEV_CREATE: CLongInt = 0x5501
  private val UI_DEV_DESTROY: CLongInt = 0x5502

  // event type constants
  val EV_KEY = 0x01
  val EV_SYN = 0x00

  // key codes
  val KEY_VOLUMEUP   = 115
  val KEY_VOLUMEDOWN = 114

  /** Initialize a virtual keyboard device that can emit volume keys */
  def init(): IO[FileOutputStream] = IO.blocking {
    val fos = new FileOutputStream("/dev/uinput")
    val fd: Int = fos.getFD.value.get

    println("startig UInput")
    
    Zone {
      println("entering zone")
      val evKeyPtr = stackalloc[Byte]()
      !evKeyPtr = 0.toByte

      val evVolUpPtr = stackalloc[Byte]()
      !evVolUpPtr = 0.toByte

      val evVolDown = stackalloc[Byte]()
      !evVolDown = 0.toByte
      println("key events allocated")

      // Enable key events
      posix.sys.ioctl.ioctl(fd, UI_SET_EVBIT, evKeyPtr)

      // Enable the specific keys
      posix.sys.ioctl.ioctl(fd, UI_SET_KEYBIT, evVolUpPtr)
      posix.sys.ioctl.ioctl(fd, UI_SET_KEYBIT, evVolDown)
    }

    println("key events set")
  
    println("sending empty array")
    // Write the struct to /dev/uinput
    fos.write(Array[Byte]())


    // Create the device
    println("creating device")
    posix.sys.ioctl.ioctl(fd, UI_DEV_CREATE, null)
    
    fos
  }

  def destroy(fos: FileOutputStream): IO[Unit] = IO.blocking {
    val fd: Int = fos.getFD.value.get
    posix.sys.ioctl.ioctl(fd, UI_DEV_DESTROY, null)
    fos.close()
  }
}

