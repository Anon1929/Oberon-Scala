package br.unb.cic.oberon.parser
import br.unb.cic.oberon.util.Resources
import scala.util.parsing.combinator._
import br.unb.cic.oberon.ast._

trait BasicParsers extends JavaTokenParsers {
    def int: Parser[IntValue] = "-?[0-9]+".r <~ not('.') ^^ (i => IntValue(i.toInt))
    def real: Parser[RealValue] = "-?[0-9]+\\.[0-9]+".r ^^ (i => RealValue(i.toDouble))
    def bool: Parser[BoolValue] = "(FALSE|TRUE)".r ^^ (i => BoolValue(i=="TRUE"))
    def string: Parser[StringValue] = "\"[^\"]+\"".r  ^^ (i => StringValue(i.substring(1, i.length()-1)))
    def char: Parser[CharValue] = ("\'[^\']\'".r)  ^^ (i => CharValue(i.charAt(1)))

    def alpha: String = "[A-z]"
    def digit: Parser[String] = "[0-9]".r ^^ (i => i)

    // Todo: Fix bug where using digit won't work. Not sure about all cases, but using "test0" failed.
    def identifier: Parser[String] = (alpha + "(" + alpha + "|" + digit + "|_)*").r ^^ (i => i)

    def typeParser: Parser[Type] = (
        "INTEGER" ^^ (i => IntegerType)
    |   "REAL" ^^ (i => RealType)
    |   "CHAR" ^^ (i => CharacterType)
    |   "BOOLEAN" ^^ (i => BooleanType)
    |   "STRING" ^^ (i => StringType)
    |   "NIL" ^^ (i => NullType)
    |   identifier ^^ (i =>  ReferenceToUserDefinedType(i))
    )
}

trait ExpressionParser extends BasicParsers {
    def aggregator(r: Expression ~ List[Expression => Expression]): Expression = { r match { case a~b => (a /: b)((acc,f) => f(acc)) } }
    def expressionParser: Parser[Expression] = addTerm ~ rep(relExpParser) ^^ aggregator
    def addTerm: Parser[Expression] = mulTerm ~ rep(addExpParser) ^^ aggregator
    def mulTerm: Parser[Expression] = fieldAccessTerm ~ rep(mulExpParser) ^^ aggregator
    def fieldAccessTerm: Parser[Expression] = factor ~ rep(fieldAccessExpParser) ^^ aggregator
    def factor: Parser[Expression] = expValueParser | "(" ~> expressionParser <~ ")" ^^ Brackets

    def relExpParser: Parser[Expression => Expression] = (
        "=" ~ addTerm ^^ { case _ ~ b => EQExpression(_, b) }
    |   "#" ~ addTerm ^^ { case _ ~ b => NEQExpression(_, b) }
    |   "<=" ~ addTerm ^^ { case _ ~ b => LTEExpression(_, b) }
    |   ">=" ~ addTerm ^^ { case _ ~ b => GTEExpression(_, b) }
    |   "<" ~ addTerm ^^ { case _ ~ b => LTExpression(_, b) }
    |   ">" ~ addTerm ^^ { case _ ~ b => GTExpression(_, b) }
    )

    def addExpParser: Parser[Expression => Expression] = (
        "+" ~ mulTerm ^^ { case _ ~ b => AddExpression(_, b) }
    |   "-" ~ mulTerm ^^ { case _ ~ b => SubExpression(_, b) }
    |   "||" ~ mulTerm ^^ { case _ ~ b => OrExpression(_, b) }
    )

    def mulExpParser: Parser[Expression => Expression] = (
        "*" ~ fieldAccessTerm ^^ { case _ ~ b => MultExpression(_, b) }
    |   "/" ~ fieldAccessTerm ^^ { case _ ~ b => DivExpression(_, b) }
    |   "&&" ~ fieldAccessTerm ^^ { case _ ~ b => AndExpression(_, b) }
    )

    // TODO: Fix bug where using '.' won't work. Temporarily using '^' instead... Don't forget to change test case!
    def fieldAccessExpParser: Parser[Expression => Expression] =
        '^' ~ identifier ^^ { case _ ~ b => FieldAccessExpression(_, b) }

    def expValueParser: Parser[Expression] = (
        int
    |   real
    |   char
    |   string
    |   bool
    |   "NIL" ^^ (i => NullValue)
    )
}

trait Oberon2ScalaParser extends ExpressionParser {

    def parseAbs[T](result: ParseResult[T]): T = {
        return result match {
            case Success(matched, _) => matched
            case Failure(msg, _)  => throw new Exception(msg)
            case Error(msg, _) => throw new Exception(msg)
        }
    }
}