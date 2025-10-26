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
 
  def fromBytes(bytes: Array[Byte]) = {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val sec   = buf.getLong()
    val usec  = buf.getLong()
    val tpe   = buf.getShort() & 0xffff
    val code  = buf.getShort() & 0xffff
    val value = buf.getInt()
    InputEvent(sec, usec, tpe, code, value)
  }
    
}