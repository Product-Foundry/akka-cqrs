package com.productfoundry.akka.serialization.json

import com.productfoundry.akka.serialization.json.TypeChoiceFormat.TypeChoiceMapping
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.reflect.ClassTag

/**
 * Define a JSON Format instance for selecting between multiple sub-types of `R`.
 * There must be no overlap between the different sub-types (none of the provided
 * sub-types can be assignable to another one).
 */
class TypeChoiceFormat[R](private val choices: Seq[TypeChoiceMapping[_ <: R]]) extends OFormat[R] {
  {
    val typeNames = choices.map(_.typeName)
    require(typeNames == typeNames.distinct, "duplicate type names in: " + typeNames.mkString(", "))
    val overlapping = choices.combinations(2).filter(pair => pair.head.erasure isAssignableFrom pair(1).erasure)
    require(overlapping.isEmpty, "overlapping choices are not allowed: " + overlapping.map(pair => pair.head.tag + " is assignable from " + pair(1).tag).mkString(", "))
  }

  /**
   * Combine two type choice formats.
   */
  def and[RR >: R](that: TypeChoiceFormat[_ <: RR]): TypeChoiceFormat[RR] =
    new TypeChoiceFormat[RR]((this.choices: Seq[TypeChoiceMapping[_ <: RR]]) ++ (that.choices: Seq[TypeChoiceMapping[_ <: RR]]))

  override def reads(json: JsValue) = for {
    (name, data) <- ((JsPath \ "type").read[String] and (JsPath \ "data").read[JsValue]).tupled.reads(json)
    mapping <- choices.find(_.typeName == name).map(mapping => JsSuccess[TypeChoiceMapping[_ <: R]](mapping)).getOrElse(JsError(s"mapping '$name' not in ${choices.map(_.typeName).mkString("[", ", ", "]")}"))
    parsed <- mapping.fromJson(data)
  } yield parsed

  override def writes(o: R) = {
    val mapping = choices.find(_.matchesInstance(o)).getOrElse(throw new IllegalArgumentException("no mapping found for instance " + o))
    Json.obj("type" -> mapping.typeName, "data" -> mapping.toJson(o))
  }
}

object TypeChoiceFormat {

  implicit class TypeChoiceMapping[A: ClassTag](typeAndFormat: (String, Format[A])) {

    val tag = implicitly[ClassTag[A]]

    def typeName = typeAndFormat._1

    def format = typeAndFormat._2

    def erasure: Class[_] = tag.runtimeClass

    def matchesInstance(o: Any): Boolean = tag.runtimeClass.isInstance(o)

    def fromJson(json: JsValue): JsResult[A] = format.reads(json)

    def toJson(o: Any): JsValue = format.writes(o.asInstanceOf[A])
  }

  def apply[R](choices: TypeChoiceMapping[_ <: R]*): TypeChoiceFormat[R] = new TypeChoiceFormat[R](choices)
}

