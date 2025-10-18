package org.polyvariant.macropad4s

import java.nio.ByteBuffer
import java.nio.ByteOrder
import InputEventCodes.*

// See https://www.kernel.org/doc/Documentation/input/input.txt section 5. Eventinterface
case class InputEvent(sec: Long, usec: Long, eventType: Int, code: Int, value: Int) {
  val isKeyPress = eventType == EV_KEY && value == 1
  val isKeyRelease = eventType == EV_KEY && value == 0 
}

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