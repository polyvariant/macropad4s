//> using platform native
//> using nativeVersion 0.5.9
//> using dep "co.fs2::fs2-io:3.12.2"

import scala.scalanative.unsafe._

import scala.scalanative.posix
import scala.scalanative.libc

import cats.effect.Concurrent
import fs2.text
import fs2.hashing.{Hashing, HashAlgorithm}
import fs2.io.file.{Files, Path}

@main
def main(): Unit = {
  println("Running keyboard grabber!")

  // Find with VENDOR="514c"; PRODUCT="8851"; for ev in /dev/input/event*; do udevadm info -q property -n "$ev" | grep -q "ID_VENDOR_ID=${VENDOR}" && udevadm info -q property -n "$ev" | grep -q "ID_MODEL_ID=${PRODUCT}" && echo "$ev"; done
  val path = "/dev/input/event11" 
  val flags = posix.fcntl.O_RDONLY

  Zone { 
    // Open the device file (e.g., a keyboard, mouse, etc.)
    val fd = posix.fcntl.open(toCString(path), flags)
    if (fd == -1) {
      println(s"Error opening device: $path")
      sys.exit(1)
    }
    val grabResult = grabFile(fd)
    if (grabResult == -1) {
      println(s"Error grabbing the device: $path")
      sys.exit(1)
    }
    println(s"Successfully grabbed the device: $path")

    println("Pres enter to release")

    val buff = libc.stdlib.malloc(1024.toSize.toCSize)
    posix.stdio.scanf(buff)
    posix.unistd.close(fd)
  }
}

def grabFile(fileDescriptor: Int): Int = {
  val EVIOCGRAB: CLongInt = 0x40044590
  val grabValue = stackalloc[Byte]()  // Allocate space for a CByte
  !grabValue = 1.toByte  // Set the value of the allocated memory to 1
  posix.sys.ioctl.ioctl(fileDescriptor, EVIOCGRAB, grabValue)  // 1 to grab the device
}