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
import fs2.io.readInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
}