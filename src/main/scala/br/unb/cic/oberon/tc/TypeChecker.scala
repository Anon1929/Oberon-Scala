package br.unb.cic.oberon.tc

import br.unb.cic.oberon.ast._
import br.unb.cic.oberon.environment.Environment
import br.unb.cic.oberon.visitor.{OberonVisitorAdapter}

class ExpressionTypeVisitor(val typeChecker: TypeChecker) extends OberonVisitorAdapter {
  type T = Option[Type]

  override def visit(t: Type): Option[Type] = t match {
    case UndefinedType => None
    case _ => Some(t)
  }
  override def visit(exp: Expression): Option[Type] = exp match {
    case Brackets(exp) => exp.accept(this)
    case IntValue(_) => Some(IntegerType)
    case RealValue(_) => Some(RealType)
    case CharValue(_) => Some(CharacterType)
    case BoolValue(_) => Some(BooleanType)
    case StringValue(_) => Some(StringType)
    case NullValue => Some(NullType)
    case Undef() => None
    case VarExpression(name) => if(typeChecker.env.lookup(name).isDefined) typeChecker.env.lookup(name).get.accept(this) else None
    case EQExpression(left, right) => computeBinExpressionType(left, right, IntegerType, BooleanType)
    case NEQExpression(left, right) => computeBinExpressionType(left, right, IntegerType, BooleanType)
    case GTExpression(left, right) => computeBinExpressionType(left, right, IntegerType, BooleanType)
    case LTExpression(left, right) => computeBinExpressionType(left, right, IntegerType, BooleanType)
    case GTEExpression(left, right) => computeBinExpressionType(left, right, IntegerType, BooleanType)
    case LTEExpression(left, right) => computeBinExpressionType(left, right, IntegerType, BooleanType)
    case AddExpression(left, right) => computeBinExpressionType(left, right, IntegerType, IntegerType) // todo passar array de tipos (sugestao)
    case SubExpression(left, right) => computeBinExpressionType(left, right, IntegerType, IntegerType)
    case MultExpression(left, right) => computeBinExpressionType(left, right, IntegerType, IntegerType)
    case DivExpression(left, right) => computeBinExpressionType(left, right, IntegerType, IntegerType)
    case AndExpression(left, right) => computeBinExpressionType(left, right, BooleanType, BooleanType)
    case OrExpression(left, right) => computeBinExpressionType(left, right, BooleanType, BooleanType)
    // TODO: function call ...

    case FieldAccessExpression(exp, attributeName) => {
      val expType = visit(exp)
      if (expType.isEmpty) None
      expType.get match {
        case ReferenceToUserDefinedType(userTypeName) => {
          val userType = typeChecker.env.lookupUserDefinedType(userTypeName)
          println(userType)
          if (userType.isEmpty) None
          val UserDefinedType(name, baseType) = userType.get
          if (baseType.isInstanceOf[RecordType]) {
            val recordType = baseType.asInstanceOf[RecordType]
            val attribute = recordType.variables.find(v => v.name.equals(attributeName))
            if(attribute.isDefined) Some(attribute.get.variableType) else None
          } else None
        }
        case RecordType(variables) => {
          val attribute = variables.find(v => v.name.equals(attributeName))
          if(attribute.isDefined) Some(attribute.get.variableType) else None
        }
        case _ => None
      }
    }

    case PointerAccessExpression(name) => {
      if(typeChecker.env.lookup(name).isDefined) {
        val pointerType = typeChecker.env.lookup(name).get.accept(this).get
        val PointerType(varType) = pointerType
        Some(varType)
      }
      else None
    }
  }

  def computeBinExpressionType(left: Expression, right: Expression, expected: Type, result: Type) : Option[Type] = {
    val t1 = left.accept(this)
    val t2 = right.accept(this)
    if(t1 == t2 && t1.contains(expected)) Some(result) else None
  }
}

class TypeChecker extends OberonVisitorAdapter {
  type T = List[(Statement, String)]

  val env =  new Environment[Type]()
  val expVisitor = new ExpressionTypeVisitor(this)

  override def visit(module: OberonModule): List[(Statement, String)] = {
    module.constants.map(c => env.setGlobalVariable(c.name, c.exp.accept(expVisitor).get))
    module.variables.map(v => env.setGlobalVariable(v.name, v.variableType))
    module.procedures.map(p => env.declareProcedure(p))
    module.userTypes.map(u => env.addUserDefinedType(u)) //added G04

    // TODO: check if the procedures are well typed.

    if(module.stmt.isDefined) module.stmt.get.accept(this)
    else List()
  }

  override def visit(stmt: Statement) = stmt match {
    case AssignmentStmt(_, _) => visitAssignment(stmt)
    case EAssignmentStmt(_, _) => visitEAssignment(stmt)
    case IfElseStmt(_, _, _) => visitIfElseStmt(stmt)
    case IfElseIfStmt(_, _, _, _) => visitIfElseIfStmt(stmt)
    case WhileStmt(_, _) => visitWhileStmt(stmt)
    case RepeatUntilStmt(_, _) => visitRepeatUntilStmt(stmt)
    case ForStmt(_, _, _) => visitForStmt(stmt)
    case LoopStmt(_) => visitLoopStmt(stmt)
    case ExitStmt() => visitExitStmt()
    case ProcedureCallStmt(_, _) => procedureCallStmt(stmt)
    case CaseStmt(_, _, _) => visitSwitchStmt(stmt)
    case SequenceStmt(stmts) => stmts.flatMap(s => s.accept(this))
    case ReturnStmt(exp) => if(exp.accept(expVisitor).isDefined) List() else List((stmt, s"Expression $exp is ill typed."))
    case ReadLongRealStmt(v) => if(env.lookup(v).isDefined) List() else List((stmt, s"Variable $v not declared."))
    case ReadRealStmt(v) => if(env.lookup(v).isDefined) List() else List((stmt, s"Variable $v not declared."))
    case ReadLongIntStmt(v) => if(env.lookup(v).isDefined) List() else List((stmt, s"Variable $v not declared."))
    case ReadIntStmt(v) => if(env.lookup(v).isDefined) List() else List((stmt, s"Variable $v not declared."))
    case ReadShortIntStmt(v) => if(env.lookup(v).isDefined) List() else List((stmt, s"Variable $v not declared."))
    case ReadCharStmt(v) => if(env.lookup(v).isDefined) List() else List((stmt, s"Variable $v not declared."))
    case WriteStmt(exp) => if(exp.accept(expVisitor).isDefined) List() else List((stmt, s"Expression $exp is ill typed."))
  }

  private def visitAssignment(stmt: Statement) = stmt match {
    case AssignmentStmt(v, exp) =>
      if (env.lookup(v).isDefined) {
        if (exp.accept(expVisitor).isDefined){
          if (env.lookup(v).get.accept(expVisitor).get != exp.accept(expVisitor).get){
              if ((env.lookup(v).get.accept(expVisitor).get.isInstanceOf[PointerType]) &&
                    (exp.accept(expVisitor).get == NullType)){
                    List()
              }
              else if ((env.lookup(v).get.accept(expVisitor).get == IntegerType) &&
                    (exp.accept(expVisitor).get == BooleanType)){
                    List()
              }
              else if ((env.lookup(v).get.accept(expVisitor).get == BooleanType) &&
                    (exp.accept(expVisitor).get == IntegerType)){
                    List()
              }              
              else{
                 List((stmt, s"Assignment between different types: $v, $exp"))
              }
          }
          else List()
        }
        else List((stmt, s"Expression $exp is ill typed"))
      }
      else List((stmt, s"Variable $v not declared"))
  }

  private def visitEAssignment(stmt: Statement) = stmt match {
    case EAssignmentStmt(designator, exp) => 
      val varType = visitAssignmentAlternative(designator)
      if (varType == exp.accept(expVisitor).get){
        List()
      }
      else List((stmt, s"Expression $exp doesn't match variable type."))
  }

  private def visitAssignmentAlternative(designator: AssignmentAlternative) = designator match {
    case PointerAssignment(pointerName) => 
      val pointer = env.lookup(pointerName).get.accept(expVisitor).get
      val PointerType(varType) = pointer
      varType
    case VarAssignment(varName) => env.lookup(varName).get.accept(expVisitor).get
    //TODO
    // case ArrayAssignment
    // case RecordAssignment
  }

  private def visitIfElseStmt(stmt: Statement) = stmt match {
    case IfElseStmt(condition, thenStmt, elseStmt) =>
      if(condition.accept(expVisitor).contains(BooleanType)) {
        val list1 = thenStmt.accept(this)
        val list2 = if(elseStmt.isDefined) elseStmt.get.accept(this) else List()
        list1 ++ list2
      }
      else List((stmt, s"Expression $condition does not have a boolean type"))
  }

  private def visitIfElseIfStmt(stmt: Statement) = stmt match {
    case IfElseIfStmt(condition, thenStmt, elseIfStmt, elseStmt) =>
      if(condition.accept(expVisitor).contains(BooleanType)){
        val list1 = thenStmt.accept(this)
        var list2 = List[(br.unb.cic.oberon.ast.Statement, String)]()
          elseIfStmt.foreach (elsif =>
            if(elsif.condition.accept(expVisitor).contains(BooleanType)){
              val list4 = elsif.thenStmt.accept(this)
              list2 = list2 ++ list4
            }else{
              val list4 = List((stmt, s"Expression $condition does not have a boolean type"))
              list2 = list2 ++ list4
            }
          )
        val list3 = if(elseStmt.isDefined) elseStmt.get.accept(this) else List()
        list2 ++ list1 ++ list3
      }
      else List((stmt, s"Expression $condition does not have a boolean type"))
  }

  private def visitWhileStmt(stmt: Statement) = stmt match {
    case WhileStmt(condition, stmt) =>
      if(condition.accept(expVisitor).contains(BooleanType)) {
        stmt.accept(this)
      }
      else List((stmt, s"Expression $condition do not have a boolean type"))
  }

  private def visitRepeatUntilStmt(stmt: Statement) = stmt match {
    case RepeatUntilStmt(condition, stmt) =>
      if(condition.accept(expVisitor).getOrElse(None) == BooleanType) {
        stmt.accept(this)
      }
      else List((stmt, s"Expression $condition do not have a boolean type"))
  }

  private def visitForStmt(stmt: Statement) = stmt match {
    case ForStmt(init, condition, stmt) =>
      if(condition.accept(expVisitor).contains(BooleanType)) {
        val list1 = init.accept(this)
        val list2 = stmt.accept(this)
        list1 ++ list2
      }
      else List((stmt, s"Expression $condition do not have a boolean type"))
  }

  private def visitLoopStmt(stmt: Statement) = stmt match {
    case LoopStmt(innerStmt) => innerStmt.accept(this)
  }

  private def visitExitStmt() = List[(br.unb.cic.oberon.ast.Statement, String)]()

  private def visitSwitchStmt(stmt: Statement) = stmt match {
    case CaseStmt(exp, cases, elseStmt) =>
      if(exp.accept(expVisitor).contains(IntegerType)){
      var list2 = List[(br.unb.cic.oberon.ast.Statement, String)]()
        cases.foreach (c =>
          c match {
            case SimpleCase(condition, stmt1) =>
              if(condition.accept(expVisitor).contains(IntegerType)) {
                val list1 = stmt1.accept(this)
                list1
              }
              else {
                val list1 = List((stmt, s"Case value $condition does not have an integer type"))
                list2 = list1 ++ list2

              }

            case RangeCase(min, max, stmt2) =>
              if(min.accept(expVisitor).contains(IntegerType) && max.accept(expVisitor).contains(IntegerType)){
                val list1 = stmt2.accept(this)
                list1
              }
              else {
                val list1 = List((stmt, s"Min $min or max $max does not have an integer type"))
                list2 = list1 ++ list2
              }
          }

        )

        val list3 = if(elseStmt.isDefined) elseStmt.get.accept(this) else List()
        list2 ++ list3
      }
      else List((stmt, s"Expression $exp does not have an integer type"))
  }

  /*
   * Type checker for a procedure call. This is the "toughest" implementation
   * here. We have to check:
   *
   * (a) the procedure exists
   * (b) the type of the actual arguments match the type of the formal arguments
   * (c) the procedure body (stmt) is well typed.
   *
   * @param stmt (a procedure call)
   *
   * @return Our error representation (statement + string with the error message)
   */
  private def procedureCallStmt(stmt: Statement): List[(Statement, String)] = stmt match {
    case ProcedureCallStmt(name, args) =>
      val procedure = env.findProcedure(name)
      if(procedure == null) return List((stmt, s"Procedure $name has not been declared."))
      else {
        // check if the type of the formal arguments and the actual arguments
        // match.
        val formalArgumentTypes = procedure.args.map(a => a.argumentType)
        val actualArgumentTypes = args.map(a => a.accept(expVisitor).get)
        // the two lists must have the same size.
        if(formalArgumentTypes.size != actualArgumentTypes.size) {
          return List((stmt, s"Wrong number of arguments to the $name procedure"))
        }
        val allTypesMatch = formalArgumentTypes.zip(actualArgumentTypes)
          .map(pair => pair._1 == pair._2)
          .forall(v => v)
        if(!allTypesMatch) {
          return List((stmt, s"The arguments do not match the $name formal arguments"))
        }
        // if everything above is ok, lets check the procedure body.
        procedure.stmt.accept(this)
      }
  }
}
