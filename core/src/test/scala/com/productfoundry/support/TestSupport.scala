package com.productfoundry.support

import java.io.{ObjectInputStream, ByteArrayInputStream, ObjectOutputStream, ByteArrayOutputStream}

object TestSupport {

  def serializeBytes(any: Any): Array[Byte] = {
    val byteArrayOutput = new ByteArrayOutputStream()
    new ObjectOutputStream(byteArrayOutput).writeObject(any)
    byteArrayOutput.toByteArray
  }

  def deserializeBytes(bytes: Array[Byte]): Any = {
    new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject()
  }
}
