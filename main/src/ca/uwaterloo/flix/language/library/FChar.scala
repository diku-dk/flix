package ca.uwaterloo.flix.language.library

import ca.uwaterloo.flix.language.ast.Name
import ca.uwaterloo.flix.language.ast.TypedAst.Type

import scala.collection.immutable

object FChar {

  // TODO: Need Char type.

  /**
    * A common super-type for all char operations.
    */
  sealed trait CharOperator extends LibraryOperator

  /**
    * All char operations.
    */
  val Ops: immutable.Map[Name.Resolved, CharOperator] = List(
    // Char Predicates.
    "Char/isAscii" -> isAscii,
    "Char/isLetter" -> isLetter,
    "Char/isDigit" -> isDigit,
    "Char/isOctDigit" -> isOctDigit,
    "Char/isHexDigit" -> isHexDigit,
    "Char/isLower" -> isLower,
    "Char/isUpper" -> isUpper,
    "Char/isSpace" -> isSpace,

    // Char Conversions.
    "Char/toLower" -> toLower,
    "Char/toUpper" -> toUpper,
    "Char/toInt" -> toInt
  ).map {
    case (name, op) => Name.Resolved.mk(name) -> op
  }.toMap

  // TODO: Replace Int by Char

  /////////////////////////////////////////////////////////////////////////////
  // Char Predicates                                                         //
  /////////////////////////////////////////////////////////////////////////////
  object isAscii extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  object isLetter extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  object isDigit extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  object isOctDigit extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  object isHexDigit extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  object isLower extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  object isUpper extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  object isSpace extends CharOperator {
    val tpe = Type.Int ~> Type.Bool
  }

  /////////////////////////////////////////////////////////////////////////////
  // Char Conversions                                                        //
  /////////////////////////////////////////////////////////////////////////////
  object toInt extends CharOperator {
    val tpe = Type.Int ~> Type.Int
  }

  object toLower extends CharOperator {
    val tpe = Type.Int ~> Type.Int
  }

  object toUpper extends CharOperator {
    val tpe = Type.Int ~> Type.Int
  }

}
