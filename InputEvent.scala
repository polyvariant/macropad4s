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