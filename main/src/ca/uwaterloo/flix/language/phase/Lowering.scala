/*
 * Copyright 2021 Magnus Madsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.uwaterloo.flix.language.phase

import ca.uwaterloo.flix.api.Flix
import ca.uwaterloo.flix.language.CompilationMessage
import ca.uwaterloo.flix.language.ast.Ast.Denotation.{Latticenal, Relational}
import ca.uwaterloo.flix.language.ast.Ast.{BoundBy, Denotation, Fixity, Modifiers, Polarity}
import ca.uwaterloo.flix.language.ast.TypedAst.Predicate.{Body, Head}
import ca.uwaterloo.flix.language.ast.TypedAst._
import ca.uwaterloo.flix.language.ast.ops.TypedAstOps
import ca.uwaterloo.flix.language.ast.{Ast, Kind, Name, Scheme, SemanticOperator, SourceLocation, SourcePosition, Symbol, Type, TypeConstructor}
import ca.uwaterloo.flix.util.Validation.ToSuccess
import ca.uwaterloo.flix.util.{InternalCompilerException, ParOps, Validation}

/**
  * This phase translates AST expressions related to the Datalog subset of the
  * language into `Fixpoint/Ast` values (which are ordinary Flix values).
  * This allows the Datalog engine to be implemented as an ordinary Flix program.
  *
  * In addition to translating expressions, types must also be translated from
  * Schema types to enum types.
  *
  * Finally, values must be boxed using the Boxable.
  */

// TODO: Long-term improvements:
// - Return a [[Validation]] from visitExp etc.
// - Decide which expressions to allow as head and body terms.

object Lowering {

  private object Defs {
    lazy val Solve: Symbol.DefnSym = Symbol.mkDefnSym("Fixpoint.solve")
    lazy val Merge: Symbol.DefnSym = Symbol.mkDefnSym("Fixpoint.union")
    lazy val Filter: Symbol.DefnSym = Symbol.mkDefnSym("Fixpoint.project")
    lazy val Rename: Symbol.DefnSym = Symbol.mkDefnSym("Fixpoint.rename")

    def ProjectInto(arity: Int): Symbol.DefnSym = Symbol.mkDefnSym(s"Fixpoint.injectInto$arity")

    def Facts(arity: Int): Symbol.DefnSym = Symbol.mkDefnSym(s"Fixpoint.facts$arity")

    lazy val DebugWithPrefix: Symbol.DefnSym = Symbol.mkDefnSym("debugWithPrefix")

    lazy val ChannelNew: Symbol.DefnSym = Symbol.mkDefnSym("Concurrent/Channel.newChannel")
    lazy val ChannelPut: Symbol.DefnSym = Symbol.mkDefnSym("Concurrent/Channel.put")
    lazy val ChannelGet: Symbol.DefnSym = Symbol.mkDefnSym("Concurrent/Channel.get")
    lazy val ChannelMpmcAdmin: Symbol.DefnSym = Symbol.mkDefnSym("Concurrent/Channel.mpmcAdmin")
    lazy val ChannelSelectFrom: Symbol.DefnSym = Symbol.mkDefnSym("Concurrent/Channel.selectFrom")
    lazy val ChannelUnsafeGetAndUnlock: Symbol.DefnSym = Symbol.mkDefnSym("Concurrent/Channel.unsafeGetAndUnlock")

    /**
      * Returns the definition associated with the given symbol `sym`.
      */
    def lookup(sym: Symbol.DefnSym)(implicit root: Root, flix: Flix): Def = root.defs.get(sym) match {
      case None => throw InternalCompilerException(s"Symbol '$sym' not found. Missing library?")
      case Some(d) => d
    }
  }

  private object Enums {
    lazy val Datalog: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.Datalog")
    lazy val Constraint: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.Constraint")

    lazy val HeadPredicate: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.HeadPredicate")
    lazy val BodyPredicate: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.BodyPredicate")

    lazy val HeadTerm: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.HeadTerm")
    lazy val BodyTerm: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.BodyTerm")

    lazy val PredSym: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Shared.PredSym")
    lazy val VarSym: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.VarSym")

    lazy val Denotation: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.Denotation")
    lazy val Polarity: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.Polarity")
    lazy val Fixity: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.Fixity")
    lazy val SourceLocation: Symbol.EnumSym = Symbol.mkEnumSym("Fixpoint/Ast.SourceLocation")

    lazy val Comparison: Symbol.EnumSym = Symbol.mkEnumSym("Comparison")
    lazy val Boxed: Symbol.EnumSym = Symbol.mkEnumSym("Boxed")

    lazy val FList: Symbol.EnumSym = Symbol.mkEnumSym("List")

    lazy val ChannelMpmc: Symbol.EnumSym = Symbol.mkEnumSym("Concurrent/Channel.Mpmc")
    lazy val ChannelMpmcAdmin: Symbol.EnumSym = Symbol.mkEnumSym("Concurrent/Channel.MpmcAdmin")

    lazy val ConcurrentReentrantLock: Symbol.EnumSym = Symbol.mkEnumSym("Concurrent/ReentrantLock.ReentrantLock")
  }

  private object Sigs {
    private val SL: SourcePosition = SourcePosition.Unknown
    private val RootNS: Name.NName = Name.NName(SL, Nil, SL)

    private def mkIdent(s: String): Name.Ident = Name.Ident(SL, s, SL)

    lazy val Box: Symbol.SigSym = Symbol.mkSigSym(Symbol.mkClassSym(RootNS, mkIdent("Boxable")), mkIdent("box"))
  }

  private object Types {
    //
    // Data Types
    //
    lazy val Datalog: Type = Type.mkEnum(Enums.Datalog, Boxed :: Nil, SourceLocation.Unknown)
    lazy val Constraint: Type = Type.mkEnum(Enums.Constraint, Boxed :: Nil, SourceLocation.Unknown)

    lazy val HeadPredicate: Type = Type.mkEnum(Enums.HeadPredicate, Boxed :: Nil, SourceLocation.Unknown)
    lazy val BodyPredicate: Type = Type.mkEnum(Enums.BodyPredicate, Boxed :: Nil, SourceLocation.Unknown)

    lazy val HeadTerm: Type = Type.mkEnum(Enums.HeadTerm, Boxed :: Nil, SourceLocation.Unknown)
    lazy val BodyTerm: Type = Type.mkEnum(Enums.BodyTerm, Boxed :: Nil, SourceLocation.Unknown)

    lazy val PredSym: Type = Type.mkEnum(Enums.PredSym, Nil, SourceLocation.Unknown)
    lazy val VarSym: Type = Type.mkEnum(Enums.VarSym, Nil, SourceLocation.Unknown)

    lazy val Denotation: Type = Type.mkEnum(Enums.Denotation, Boxed :: Nil, SourceLocation.Unknown)
    lazy val Polarity: Type = Type.mkEnum(Enums.Polarity, Nil, SourceLocation.Unknown)
    lazy val Fixity: Type = Type.mkEnum(Enums.Fixity, Nil, SourceLocation.Unknown)
    lazy val SL: Type = Type.mkEnum(Enums.SourceLocation, Nil, SourceLocation.Unknown)

    lazy val Comparison: Type = Type.mkEnum(Enums.Comparison, Nil, SourceLocation.Unknown)
    lazy val Boxed: Type = Type.mkEnum(Enums.Boxed, Nil, SourceLocation.Unknown)

    lazy val ChannelMpmcAdmin: Type = Type.mkEnum(Enums.ChannelMpmcAdmin, Nil, SourceLocation.Unknown)

    lazy val ConcurrentReentrantLock: Type = Type.mkEnum(Enums.ConcurrentReentrantLock, Nil, SourceLocation.Unknown)

    def mkList(t: Type, loc: SourceLocation): Type = Type.mkEnum(Enums.FList, List(t), loc)

    //
    // Function Types.
    //
    lazy val SolveType: Type = Type.mkPureArrow(Datalog, Datalog, SourceLocation.Unknown)
    lazy val MergeType: Type = Type.mkPureUncurriedArrow(List(Datalog, Datalog), Datalog, SourceLocation.Unknown)
    lazy val FilterType: Type = Type.mkPureUncurriedArrow(List(PredSym, Datalog), Datalog, SourceLocation.Unknown)
    lazy val RenameType: Type = Type.mkPureUncurriedArrow(List(mkList(PredSym, SourceLocation.Unknown), Datalog), Datalog, SourceLocation.Unknown)
  }

  /**
    * Translates internal Datalog constraints into Flix Datalog constraints.
    */
  def run(root: Root)(implicit flix: Flix): Validation[Root, CompilationMessage] = flix.phase("Lowering") {
    val defs = ParOps.parMap(root.defs.values)((d: Def) => visitDef(d)(root, flix))
    val sigs = ParOps.parMap(root.sigs.values)((s: Sig) => visitSig(s)(root, flix))
    val instances = ParOps.parMap(root.instances.values)((insts: List[Instance]) => insts.map(i => visitInstance(i)(root, flix)))
    val enums = ParOps.parMap(root.enums.values)((e: Enum) => visitEnum(e)(root, flix))

    val newDefs = defs.map(kv => kv.sym -> kv).toMap
    val newSigs = sigs.map(kv => kv.sym -> kv).toMap
    val newInstances = instances.map(kv => kv.head.sym.clazz -> kv).toMap
    val newEnums = enums.map(kv => kv.sym -> kv).toMap

    // Sigs are shared between the `sigs` field and the `classes` field.
    // Instead of visiting twice, we visit the `sigs` field and then look up the results when visiting classes.
    val classes = ParOps.parMap(root.classes.values)((c: Class) => visitClass(c, newSigs)(root, flix))
    val newClasses = classes.map(kv => kv.sym -> kv).toMap
    root.copy(defs = newDefs, sigs = newSigs, instances = newInstances, enums = newEnums, classes = newClasses).toSuccess
  }

  /**
    * Lowers the given definition `defn0`.
    */
  private def visitDef(defn0: Def)(implicit root: Root, flix: Flix): Def = defn0 match {
    case Def(sym, spec0, impl0) =>
      val spec = visitSpec(spec0)
      val impl = visitImpl(impl0)
      Def(sym, spec, impl)
  }

  /**
    * Lowers the given signature `sig0`.
    */
  private def visitSig(sig0: Sig)(implicit root: Root, flix: Flix): Sig = sig0 match {
    case Sig(sym, spec0, impl0) =>
      val spec = visitSpec(spec0)
      val impl = impl0.map(visitImpl)
      Sig(sym, spec, impl)
  }

  /**
    * Lowers the given instance `inst0`.
    */
  private def visitInstance(inst0: Instance)(implicit root: Root, flix: Flix): Instance = inst0 match {
    case Instance(doc, ann, mod, sym, tpe0, tconstrs0, defs0, ns, loc) =>
      val tpe = visitType(tpe0)
      val tconstrs = tconstrs0.map(visitTypeConstraint)
      val defs = defs0.map(visitDef)
      Instance(doc, ann, mod, sym, tpe, tconstrs, defs, ns, loc)
  }

  /**
    * Lowers the given enum `enum0`.
    */
  private def visitEnum(enum0: Enum)(implicit root: Root, flix: Flix): Enum = enum0 match {
    case Enum(doc, ann, mod, sym, tparams, derives, cases0, tpe0, loc) =>
      val tpe = visitType(tpe0)
      val cases = cases0.map {
        case (_, Case(caseSym, caseTpeDeprecated0, caseSc0, loc)) =>
          val caseTpeDeprecated = visitType(caseTpeDeprecated0)
          val caseSc = visitScheme(caseSc0)
          (caseSym, Case(caseSym, caseTpeDeprecated, caseSc, loc))
      }
      Enum(doc, ann, mod, sym, tparams, derives, cases, tpe, loc)
  }

  /**
    * Lowers the given type constraint `tconstr0`.
    */
  private def visitTypeConstraint(tconstr0: Ast.TypeConstraint)(implicit root: Root, flix: Flix): Ast.TypeConstraint = tconstr0 match {
    case Ast.TypeConstraint(head, tpe0, loc) =>
      val tpe = visitType(tpe0)
      Ast.TypeConstraint(head, tpe, loc)
  }

  /**
    * Lowers the given class `clazz0`, with the given lowered sigs `sigs`.
    */
  private def visitClass(clazz0: Class, sigs: Map[Symbol.SigSym, Sig])(implicit root: Root, flix: Flix): Class = clazz0 match {
    case Class(doc, ann, mod, sym, tparam, superClasses0, signatures0, laws0, loc) =>
      val superClasses = superClasses0.map(visitTypeConstraint)
      val signatures = signatures0.map(sig => sigs(sig.sym))
      val laws = laws0.map(visitDef)
      Class(doc, ann, mod, sym, tparam, superClasses, signatures, laws, loc)
  }

  /**
    * Lowers the given `spec0`.
    */
  private def visitSpec(spec0: Spec)(implicit root: Root, flix: Flix): Spec = spec0 match {
    case Spec(doc, ann, mod, tparams, fparams, declaredScheme, retTpe, pur, eff, tconstrs, loc) =>
      val fs = fparams.map(visitFormalParam)
      val ds = visitScheme(declaredScheme)
      Spec(doc, ann, mod, tparams, fs, ds, retTpe, pur, eff, tconstrs, loc)
  }

  /**
    * Lowers the given `impl0`.
    */
  private def visitImpl(impl0: Impl)(implicit root: Root, flix: Flix): Impl = impl0 match {
    case Impl(exp, inferredScheme) =>
      val e = visitExp(exp)
      val s = visitScheme(inferredScheme)
      Impl(e, s)
  }

  /**
    * Lowers the given expression `exp0`.
    */
  private def visitExp(exp0: Expression)(implicit root: Root, flix: Flix): Expression = exp0 match {
    case Expression.Unit(_) => exp0

    case Expression.Null(tpe, loc) =>
      val t = visitType(tpe)
      Expression.Null(t, loc)

    case Expression.True(_) => exp0

    case Expression.False(_) => exp0

    case Expression.Char(_, _) => exp0

    case Expression.Float32(_, _) => exp0

    case Expression.Float64(_, _) => exp0

    case Expression.BigDecimal(_, _) => exp0

    case Expression.Int8(_, _) => exp0

    case Expression.Int16(_, _) => exp0

    case Expression.Int32(_, _) => exp0

    case Expression.Int64(_, _) => exp0

    case Expression.BigInt(_, _) => exp0

    case Expression.Str(_, _) => exp0

    case Expression.Wild(tpe, loc) =>
      val t = visitType(tpe)
      Expression.Wild(t, loc)

    case Expression.Var(sym, tpe, loc) =>
      val t = visitType(tpe)
      Expression.Var(sym, t, loc)

    case Expression.Def(sym, tpe, loc) =>
      val t = visitType(tpe)
      Expression.Def(sym, t, loc)

    case Expression.Sig(sym, tpe, loc) =>
      val t = visitType(tpe)
      Expression.Sig(sym, t, loc)

    case Expression.Hole(sym, tpe, loc) =>
      val t = visitType(tpe)
      Expression.Hole(sym, t, loc)

    case Expression.Lambda(fparam, exp, tpe, loc) =>
      val p = visitFormalParam(fparam)
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Lambda(p, e, t, loc)

    case Expression.Apply(exp, exps, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val es = visitExps(exps)
      val t = visitType(tpe)
      Expression.Apply(e, es, t, pur, eff, loc)

    case Expression.Unary(sop, exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Unary(sop, e, t, pur, eff, loc)

    case Expression.Binary(sop, exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val t = visitType(tpe)
      Expression.Binary(sop, e1, e2, t, pur, eff, loc)

    case Expression.Let(sym, mod, exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val t = visitType(tpe)
      Expression.Let(sym, mod, e1, e2, t, pur, eff, loc)

    case Expression.LetRec(sym, mod, exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val t = visitType(tpe)
      Expression.LetRec(sym, mod, e1, e2, t, pur, eff, loc)

    case Expression.Region(_, loc) =>
      // Introduce a Unit value to represent the Region value.
      Expression.Unit(loc)

    case Expression.Scope(sym, regionVar, exp, tpe, pur, eff, loc) =>
      // Introduce a Unit value to represent the Region value.
      val mod = Ast.Modifiers.Empty
      val e1 = Expression.Unit(loc)
      val e2 = visitExp(exp)
      Expression.Let(sym, mod, e1, e2, tpe, pur, eff, loc)

    case Expression.IfThenElse(exp1, exp2, exp3, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val e3 = visitExp(exp3)
      val t = visitType(tpe)
      Expression.IfThenElse(e1, e2, e3, t, pur, eff, loc)

    case Expression.Stm(exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val t = visitType(tpe)
      Expression.Stm(e1, e2, t, pur, eff, loc)

    case Expression.Discard(exp, pur, eff, loc) =>
      val e = visitExp(exp)
      Expression.Discard(e, pur, eff, loc)

    case Expression.Match(exp, rules, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val rs = rules.map(visitMatchRule)
      val t = visitType(tpe)
      Expression.Match(e, rs, t, pur, eff, loc)

    case Expression.TypeMatch(exp, rules, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val rs = rules.map(visitMatchTypeRule)
      val t = visitType(tpe)
      Expression.TypeMatch(e, rs, t, pur, eff, loc)

    case Expression.Choose(exps, rules, tpe, pur, eff, loc) =>
      val es = visitExps(exps)
      val rs = rules.map(visitChoiceRule)
      val t = visitType(tpe)
      Expression.Choose(es, rs, t, pur, eff, loc)

    case Expression.Tag(sym, exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Tag(sym, e, t, pur, eff, loc)

    case Expression.Tuple(elms, tpe, pur, eff, loc) =>
      val es = visitExps(elms)
      val t = visitType(tpe)
      Expression.Tuple(es, t, pur, eff, loc)

    case Expression.RecordEmpty(tpe, loc) =>
      val t = visitType(tpe)
      Expression.RecordEmpty(t, loc)

    case Expression.RecordSelect(exp, field, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.RecordSelect(e, field, t, pur, eff, loc)

    case Expression.RecordExtend(field, value, rest, tpe, pur, eff, loc) =>
      val v = visitExp(value)
      val r = visitExp(rest)
      val t = visitType(tpe)
      Expression.RecordExtend(field, v, r, t, pur, eff, loc)

    case Expression.RecordRestrict(field, rest, tpe, pur, eff, loc) =>
      val r = visitExp(rest)
      val t = visitType(tpe)
      Expression.RecordRestrict(field, r, t, pur, eff, loc)

    case Expression.ArrayLit(exps, exp, tpe, pur, eff, loc) =>
      val es = visitExps(exps)
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.ArrayLit(es, e, t, pur, eff, loc)

    case Expression.ArrayNew(exp1, exp2, exp3, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val e3 = visitExp(exp3)
      val t = visitType(tpe)
      Expression.ArrayNew(e1, e2, e3, t, pur, eff, loc)

    case Expression.ArrayLoad(base, index, tpe, pur, eff, loc) =>
      val b = visitExp(base)
      val i = visitExp(index)
      val t = visitType(tpe)
      Expression.ArrayLoad(b, i, t, pur, eff, loc)

    case Expression.ArrayLength(base, pur, eff, loc) =>
      val b = visitExp(base)
      Expression.ArrayLength(b, pur, eff, loc)

    case Expression.ArrayStore(base, index, elm, pur, eff, loc) =>
      val b = visitExp(base)
      val i = visitExp(index)
      val e = visitExp(elm)
      Expression.ArrayStore(b, i, e, pur, eff, loc)

    case Expression.ArraySlice(base, beginIndex, endIndex, tpe, pur, eff, loc) =>
      val b = visitExp(base)
      val bi = visitExp(beginIndex)
      val ei = visitExp(endIndex)
      val t = visitType(tpe)
      Expression.ArraySlice(b, bi, ei, t, pur, eff, loc)

    case Expression.Ref(exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val t = visitType(tpe)
      Expression.Ref(e1, e2, t, pur, eff, loc)

    case Expression.Deref(exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Deref(e, t, pur, eff, loc)

    case Expression.Assign(exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val t = visitType(tpe)
      Expression.Assign(e1, e2, t, pur, eff, loc)

    case Expression.Ascribe(exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Ascribe(e, t, pur, eff, loc)

    case Expression.Cast(exp, declaredType, declaredPur, declaredEff, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val dt = declaredType.map(visitType)
      val t = visitType(tpe)
      Expression.Cast(e, dt, declaredPur, declaredEff, t, pur, eff, loc)

    case Expression.Mask(exp, _, _, _, _) =>
      visitExp(exp)

    case Expression.Upcast(exp, tpe, loc) =>
      Expression.Upcast(visitExp(exp), visitType(tpe), loc)

    case Expression.Without(exp, sym, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Without(e, sym, t, pur, eff, loc)

    case Expression.TryCatch(exp, rules, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val rs = rules.map(visitCatchRule)
      val t = visitType(tpe)
      Expression.TryCatch(e, rs, t, pur, eff, loc)

    case Expression.TryWith(exp, sym, rules, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val rs = rules.map(visitHandlerRule)
      val t = visitType(tpe)
      Expression.TryWith(e, sym, rs, t, pur, eff, loc)

    case Expression.Do(sym, exps, pur, eff, loc) =>
      val es = visitExps(exps)
      Expression.Do(sym, es, pur, eff, loc)

    case Expression.Resume(exp, tpe, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Resume(e, t, loc)

    case Expression.InvokeConstructor(constructor, args, tpe, pur, eff, loc) =>
      val as = visitExps(args)
      val t = visitType(tpe)
      Expression.InvokeConstructor(constructor, as, t, pur, eff, loc)

    case Expression.InvokeMethod(method, exp, args, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val as = visitExps(args)
      val t = visitType(tpe)
      Expression.InvokeMethod(method, e, as, t, pur, eff, loc)

    case Expression.InvokeStaticMethod(method, args, tpe, pur, eff, loc) =>
      val as = visitExps(args)
      val t = visitType(tpe)
      Expression.InvokeStaticMethod(method, as, t, pur, eff, loc)

    case Expression.GetField(field, exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.GetField(field, e, t, pur, eff, loc)

    case Expression.PutField(field, exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val t = visitType(tpe)
      Expression.PutField(field, e1, e2, t, pur, eff, loc)

    case Expression.GetStaticField(field, tpe, pur, eff, loc) =>
      val t = visitType(tpe)
      Expression.GetStaticField(field, t, pur, eff, loc)

    case Expression.PutStaticField(field, exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.PutStaticField(field, e, t, pur, eff, loc)

    case Expression.NewObject(name, clazz, tpe, pur, eff, methods, loc) =>
      val t = visitType(tpe)
      val ms = methods.map(visitJvmMethod)
      Expression.NewObject(name, clazz, t, pur, eff, ms, loc)

    // New channel expressions are rewritten as follows:
    //     chan Int32 10
    // becomes a call to the standard library function:
    //     Concurrent/Channel.newChannel(10)
    //
    case Expression.NewChannel(exp, tpe, elmTpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      val chTpe = mkChannelTpe(elmTpe, loc)
      val ch = mkNewChannel(e, chTpe, pur, eff, loc)
      val sym = mkLetSym("ch", loc)
      val tuple = Expression.Tuple(List(Expression.Var(sym, chTpe, loc), Expression.Var(sym, chTpe, loc)), t, pur, eff, loc)
      Expression.Let(sym, Modifiers(List(Ast.Modifier.Synthetic)), ch, tuple, chTpe, pur, eff, loc)

    // Channel get expressions are rewritten as follows:
    //     <- c
    // becomes a call to the standard library function:
    //     Concurrent/Channel.get(c)
    //
    case Expression.GetChannel(exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      mkGetChannel(e, t, pur, eff, loc)

    // Channel put expressions are rewritten as follows:
    //     c <- 42
    // becomes a call to the standard library function:
    //     Concurrent/Channel.put(42, c)
    //
    case Expression.PutChannel(exp1, exp2, _, pur, eff, loc) =>
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      mkPutChannel(e1, e2, pur, eff, loc)

    // Channel select expressions are rewritten as follows:
    //     select {
    //         case x <- ?ch1 => ?handlech1
    //         case y <- ?ch2 => ?handlech2
    //         case _ => ?default
    //     }
    // becomes:
    //     let ch1 = ?ch1;
    //     let ch2 = ?ch2;
    //     match selectFrom([mpmcAdmin(ch1), mpmcAdmin(ch2)]) @ Static, false) {  // true if no default
    //         case (0, locks) =>
    //             let x = unsafeGetAndUnlock(ch1, locks);
    //             ?handlech1
    //         case (1, locks) =>
    //             let y = unsafeGetAndUnlock(ch2, locks);
    //             ?handlech2
    //         case (-1, _) =>                                                  // Omitted if no default
    //             ?default                                                     // Unlock is handled by selectFrom
    //     }
    // Note: match is not exhaustive: we're relying on the simplifier to handle this for us
    //
    case Expression.SelectChannel(rules, default, tpe, pur, eff, loc) =>
      val rs = rules.map(visitSelectChannelRule)
      val d = default.map(visitExp)
      val t = visitType(tpe)

      val channels = rs map { case SelectChannelRule(_, c, _) => (mkLetSym("chan", loc), c) }
      val adminArray = mkChannelAdminArray(rs, channels, loc)
      val selectExp = mkChannelSelect(adminArray, d, loc)
      val cases = mkChannelCases(rs, channels, pur, eff, loc)
      val defaultCase = mkSelectDefaultCase(d, t, loc)
      val matchExp = Expression.Match(selectExp, cases ++ defaultCase, t, pur, eff, loc)

      channels.foldRight[Expression](matchExp) {
        case ((sym, c), e) => Expression.Let(sym, Modifiers.Empty, c, e, t, pur, eff, loc)
      }

    case Expression.Spawn(exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Spawn(e, t, pur, eff, loc)

    case Expression.Par(exp, loc0) => exp match {
      case Expression.Tuple(elms, tpe, pur, eff, loc1) =>
        val es = visitExps(elms)
        val t = visitType(tpe)
        val e = mkParTuple(Expression.Tuple(es, t, pur, eff, loc1))
        Expression.Cast(e, None, Some(Type.Pure), Some(Type.Empty), t, pur, eff, loc0)

      case _ =>
        throw InternalCompilerException(s"Unexpected par expression near ${exp.loc.format}: $exp")
    }

    case Expression.ParYield(frags, exp, tpe, pur, eff, loc) =>
      val fs = frags.map {
        case ParYieldFragment(pat, e, loc) => ParYieldFragment(visitPat(pat), visitExp(e), loc)
      }
      val e = visitExp(exp)
      val t = visitType(tpe)
      val e1 = Expression.ParYield(fs, e, t, pur, eff, loc)
      mkParYield(e1)

    case Expression.Lazy(exp, tpe, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Lazy(e, t, loc)

    case Expression.Force(exp, tpe, pur, eff, loc) =>
      val e = visitExp(exp)
      val t = visitType(tpe)
      Expression.Force(e, t, pur, eff, loc)

    case Expression.FixpointConstraintSet(cs, _, _, loc) =>
      mkDatalog(cs, loc)

    case Expression.FixpointLambda(pparams, exp, _, _, pur, eff, loc) =>
      val defn = Defs.lookup(Defs.Rename)
      val defExp = Expression.Def(defn.sym, Types.RenameType, loc)
      val predExps = mkList(pparams.map(pparam => mkPredSym(pparam.pred)), Types.mkList(Types.PredSym, loc), loc)
      val argExps = predExps :: visitExp(exp) :: Nil
      val resultType = Types.Datalog
      Expression.Apply(defExp, argExps, resultType, pur, eff, loc)

    case Expression.FixpointMerge(exp1, exp2, _, _, pur, eff, loc) =>
      val defn = Defs.lookup(Defs.Merge)
      val defExp = Expression.Def(defn.sym, Types.MergeType, loc)
      val argExps = visitExp(exp1) :: visitExp(exp2) :: Nil
      val resultType = Types.Datalog
      Expression.Apply(defExp, argExps, resultType, pur, eff, loc)

    case Expression.FixpointSolve(exp, _, _, pur, eff, loc) =>
      val defn = Defs.lookup(Defs.Solve)
      val defExp = Expression.Def(defn.sym, Types.SolveType, loc)
      val argExps = visitExp(exp) :: Nil
      val resultType = Types.Datalog
      Expression.Apply(defExp, argExps, resultType, pur, eff, loc)

    case Expression.FixpointFilter(pred, exp, _, pur, eff, loc) =>
      val defn = Defs.lookup(Defs.Filter)
      val defExp = Expression.Def(defn.sym, Types.FilterType, loc)
      val argExps = mkPredSym(pred) :: visitExp(exp) :: Nil
      val resultType = Types.Datalog
      Expression.Apply(defExp, argExps, resultType, pur, eff, loc)

    case Expression.FixpointInject(exp, pred, _, pur, eff, loc) =>
      // Compute the arity of the functor F[(a, b, c)] or F[a].
      val arity = Type.eraseAliases(exp.tpe) match {
        case Type.Apply(_, innerType, _) => innerType.typeConstructor match {
          case Some(TypeConstructor.Tuple(l)) => l
          case Some(TypeConstructor.Unit) => 0
          case _ => 1
        }
        case _ => throw InternalCompilerException(s"Unexpected non-foldable type: '${exp.tpe}'.")
      }

      // Compute the symbol of the function.
      val sym = Defs.ProjectInto(arity)

      // The type of the function.
      val defTpe = Type.mkPureUncurriedArrow(List(Types.PredSym, exp.tpe), Types.Datalog, loc)

      // Put everything together.
      val defExp = Expression.Def(sym, defTpe, loc)
      val argExps = mkPredSym(pred) :: visitExp(exp) :: Nil
      Expression.Apply(defExp, argExps, Types.Datalog, pur, eff, loc)

    case Expression.FixpointProject(pred, exp, tpe, pur, eff, loc) =>
      // Compute the arity of the predicate symbol.
      // The type is either of the form `Array[(a, b, c)]` or `Array[a]`.
      val arity = Type.eraseAliases(tpe) match {
        case Type.Apply(Type.Cst(_, _), innerType, _) => innerType.typeConstructor match {
          case Some(TypeConstructor.Tuple(_)) => innerType.typeArguments.length
          case Some(TypeConstructor.Unit) => 0
          case _ => 1
        }
        case _ => throw InternalCompilerException(s"Unexpected non-list type: '$tpe'.")
      }

      // Compute the symbol of the function.
      val sym = Defs.Facts(arity)

      // The type of the function.
      val defTpe = Type.mkPureUncurriedArrow(List(Types.PredSym, Types.Datalog), tpe, loc)

      // Put everything together.
      val defExp = Expression.Def(sym, defTpe, loc)
      val argExps = mkPredSym(pred) :: visitExp(exp) :: Nil
      Expression.Apply(defExp, argExps, tpe, pur, eff, loc)

    case Expression.Reify(t0, tpe0, pur, eff, loc) =>
      val t = visitType(t0)
      val tpe = visitType(tpe0)
      Expression.Reify(t, tpe, pur, eff, loc)

    case Expression.ReifyType(t0, k, tpe0, pur, eff, loc) =>
      val t = visitType(t0)
      val tpe = visitType(tpe0)
      Expression.ReifyType(t, k, tpe, pur, eff, loc)

    case Expression.ReifyEff(sym, exp1, exp2, exp3, tpe, pur, eff, loc) =>
      val t = visitType(tpe)
      val e1 = visitExp(exp1)
      val e2 = visitExp(exp2)
      val e3 = visitExp(exp3)
      Expression.ReifyEff(sym, e1, e2, e3, t, pur, eff, loc)
  }

  /**
    * Lowers the given list of expressions `exps0`.
    */
  private def visitExps(exps0: List[Expression])(implicit root: Root, flix: Flix): List[Expression] = exps0.map(visitExp)

  /**
    * Lowers the given pattern `pat0`.
    */
  private def visitPat(pat0: Pattern)(implicit root: Root, flix: Flix): Pattern = pat0 match {
    case Pattern.Wild(tpe, loc) =>
      val t = visitType(tpe)
      Pattern.Wild(t, loc)

    case Pattern.Var(sym, tpe, loc) =>
      val t = visitType(tpe)
      Pattern.Var(sym, t, loc)

    case Pattern.Unit(_) => pat0

    case Pattern.True(_) => pat0

    case Pattern.False(_) => pat0

    case Pattern.Char(_, _) => pat0

    case Pattern.Float32(_, _) => pat0

    case Pattern.Float64(_, _) => pat0

    case Pattern.BigDecimal(_, _) => pat0

    case Pattern.Int8(_, _) => pat0

    case Pattern.Int16(_, _) => pat0

    case Pattern.Int32(_, _) => pat0

    case Pattern.Int64(_, _) => pat0

    case Pattern.BigInt(_, _) => pat0

    case Pattern.Str(_, _) => pat0

    case Pattern.Tag(sym, pat, tpe, loc) =>
      val p = visitPat(pat)
      val t = visitType(tpe)
      Pattern.Tag(sym, p, t, loc)

    case Pattern.Tuple(elms, tpe, loc) =>
      val es = elms.map(visitPat)
      val t = visitType(tpe)
      Pattern.Tuple(es, t, loc)

    case Pattern.Array(elms, tpe, loc) =>
      val es = elms.map(visitPat)
      val t = visitType(tpe)
      Pattern.Array(es, t, loc)

    case Pattern.ArrayTailSpread(elms, sym, tpe, loc) =>
      val es = elms.map(visitPat)
      val t = visitType(tpe)
      Pattern.ArrayTailSpread(es, sym, t, loc)

    case Pattern.ArrayHeadSpread(sym, elms, tpe, loc) =>
      val es = elms.map(visitPat)
      val t = visitType(tpe)
      Pattern.ArrayHeadSpread(sym, es, t, loc)
  }

  /**
    * Lowers the given scheme `sc0`.
    */
  private def visitScheme(sc0: Scheme)(implicit root: Root, flix: Flix): Scheme = sc0 match {
    case Scheme(quantifiers, constraints, base) =>
      // TODO: What about constraints?
      val b = visitType(base)
      Scheme(quantifiers, constraints, b)
  }

  /**
    * Lowers the given type `tpe0`.
    */
  private def visitType(tpe0: Type)(implicit root: Root, flix: Flix): Type = {
    def visit(tpe: Type): Type = tpe match {
      case Type.Var(sym, loc) => sym.kind match {
        case Kind.SchemaRow => Type.Var(sym.withKind(Kind.Star), loc)
        case _ => tpe0
      }

      // Special case for Sender[_] and Receiver[_], both of which are rewritten to Concurrent/Channel.Mpmc
      case Type.Cst(TypeConstructor.Sender, loc) =>
        Type.Cst(TypeConstructor.Enum(Enums.ChannelMpmc, Kind.Star ->: Kind.Star), loc)

      case Type.Cst(TypeConstructor.Receiver, loc) =>
        Type.Cst(TypeConstructor.Enum(Enums.ChannelMpmc, Kind.Star ->: Kind.Star), loc)

      case Type.Cst(_, _) => tpe0

      case Type.Apply(tpe1, tpe2, loc) =>
        val t1 = visitType(tpe1)
        val t2 = visitType(tpe2)
        Type.Apply(t1, t2, loc)

      case Type.Alias(sym, args, t, loc) => Type.Alias(sym, args.map(visit), visit(t), loc)

    }

    if (tpe0.typeConstructor.contains(TypeConstructor.Schema))
      Types.Datalog
    else
      visit(tpe0)
  }

  /**
    * Lowers the given formal parameter `fparam0`.
    */
  private def visitFormalParam(fparam0: FormalParam)(implicit root: Root, flix: Flix): FormalParam = fparam0 match {
    case FormalParam(sym, mod, tpe, src, loc) =>
      val t = visitType(tpe)
      FormalParam(sym, mod, t, src, loc)
  }

  /**
    * Lowers the given choice rule `rule0`.
    */
  private def visitChoiceRule(rule0: ChoiceRule)(implicit root: Root, flix: Flix): ChoiceRule = rule0 match {
    case ChoiceRule(pat, exp) =>
      val p = pat.map {
        case p@ChoicePattern.Wild(_) => p
        case p@ChoicePattern.Absent(_) => p
        case ChoicePattern.Present(sym, tpe, loc) =>
          val t = visitType(tpe)
          ChoicePattern.Present(sym, t, loc)
      }
      val e = visitExp(exp)
      ChoiceRule(p, e)
  }

  /**
    * Lowers the given catch rule `rule0`.
    */
  private def visitCatchRule(rule0: CatchRule)(implicit root: Root, flix: Flix): CatchRule = rule0 match {
    case CatchRule(sym, clazz, exp) =>
      val e = visitExp(exp)
      CatchRule(sym, clazz, e)
  }

  /**
    * Lowers the given handler rule `rule0`.
    */
  private def visitHandlerRule(rule0: HandlerRule)(implicit root: Root, flix: Flix): HandlerRule = rule0 match {
    case HandlerRule(sym, fparams, exp) =>
      val e = visitExp(exp)
      HandlerRule(sym, fparams, e)
  }

  /**
    * Lowers the given match rule `rule0`.
    */
  private def visitMatchRule(rule0: MatchRule)(implicit root: Root, flix: Flix): MatchRule = rule0 match {
    case MatchRule(pat, guard, exp) =>
      val p = visitPat(pat)
      val g = guard.map(visitExp)
      val e = visitExp(exp)
      MatchRule(p, g, e)
  }

  /**
    * Lowers the given match rule `rule0`.
    */
  private def visitMatchTypeRule(rule0: MatchTypeRule)(implicit root: Root, flix: Flix): MatchTypeRule = rule0 match {
    case MatchTypeRule(sym, tpe, exp) =>
      val e = visitExp(exp)
      MatchTypeRule(sym, tpe, e)
  }

  /**
    * Lowers the given select channel rule `rule0`.
    */
  private def visitSelectChannelRule(rule0: SelectChannelRule)(implicit root: Root, flix: Flix): SelectChannelRule = rule0 match {
    case SelectChannelRule(sym, chan, exp) =>
      val c = visitExp(chan)
      val e = visitExp(exp)
      SelectChannelRule(sym, c, e)
  }

  /**
    * Constructs a `Fixpoint/Ast.Datalog` value from the given list of Datalog constraints `cs`.
    */
  private def mkDatalog(cs: List[Constraint], loc: SourceLocation)(implicit root: Root, flix: Flix): Expression = {
    val factExps = cs.filter(c => c.body.isEmpty).map(visitConstraint)
    val ruleExps = cs.filter(c => c.body.nonEmpty).map(visitConstraint)

    val factListExp = mkList(factExps, Types.Constraint, loc)
    val ruleListExp = mkList(ruleExps, Types.Constraint, loc)

    val innerExp = mkTuple(List(factListExp, ruleListExp), loc)
    mkTag(Enums.Datalog, "Datalog", innerExp, Types.Datalog, loc)
  }

  /**
    * Lowers the given constraint `c0`.
    */
  private def visitConstraint(c0: Constraint)(implicit root: Root, flix: Flix): Expression = c0 match {
    case Constraint(cparams, head, body, loc) =>
      val headExp = visitHeadPred(cparams, head)
      val bodyExp = mkList(body.map(visitBodyPred(cparams, _)), Types.BodyPredicate, loc)
      val innerExp = mkTuple(headExp :: bodyExp :: Nil, loc)
      mkTag(Enums.Constraint, "Constraint", innerExp, Types.Constraint, loc)
  }

  /**
    * Lowers the given head predicate `p0`.
    */
  private def visitHeadPred(cparams0: List[ConstraintParam], p0: Predicate.Head)(implicit root: Root, flix: Flix): Expression = p0 match {
    case Head.Atom(pred, den, terms, _, loc) =>
      val predSymExp = mkPredSym(pred)
      val denotationExp = mkDenotation(den, terms.lastOption.map(_.tpe), loc)
      val termsExp = mkList(terms.map(visitHeadTerm(cparams0, _)), Types.HeadTerm, loc)
      val innerExp = mkTuple(predSymExp :: denotationExp :: termsExp :: Nil, loc)
      mkTag(Enums.HeadPredicate, "HeadAtom", innerExp, Types.HeadPredicate, loc)
  }

  /**
    * Lowers the given body predicate `p0`.
    */
  private def visitBodyPred(cparams0: List[ConstraintParam], p0: Predicate.Body)(implicit root: Root, flix: Flix): Expression = p0 match {
    case Body.Atom(pred, den, polarity, fixity, terms, _, loc) =>
      val predSymExp = mkPredSym(pred)
      val denotationExp = mkDenotation(den, terms.lastOption.map(_.tpe), loc)
      val polarityExp = mkPolarity(polarity, loc)
      val fixityExp = mkFixity(fixity, loc)
      val termsExp = mkList(terms.map(visitBodyTerm(cparams0, _)), Types.BodyTerm, loc)
      val innerExp = mkTuple(predSymExp :: denotationExp :: polarityExp :: fixityExp :: termsExp :: Nil, loc)
      mkTag(Enums.BodyPredicate, "BodyAtom", innerExp, Types.BodyPredicate, loc)

    case Body.Guard(exp0, loc) =>
      // Compute the universally quantified variables (i.e. the variables not bound by the local scope).
      val quantifiedFreeVars = quantifiedVars(cparams0, exp0)
      mkGuard(quantifiedFreeVars, exp0, loc)

    case Body.Loop(varSyms, exp, loc) =>
      ??? // TODO
  }

  /**
    * Lowers the given head term `exp0`.
    */
  private def visitHeadTerm(cparams0: List[ConstraintParam], exp0: Expression)(implicit root: Root, flix: Flix): Expression = {
    //
    // We need to consider four cases:
    //
    // Case 1.1: The expression is quantified variable. We translate it to a Var.
    // Case 1.2: The expression is a lexically bound variable. We translate it to a Lit that captures its value.
    // Case 2: The expression does not contain a quantified variable. We evaluate it to a (boxed) value.
    // Case 3: The expression contains quantified variables. We translate it to an application term.
    //
    exp0 match {
      case Expression.Var(sym, _, _) =>
        // Case 1: Variable term.
        if (isQuantifiedVar(sym, cparams0)) {
          // Case 1.1: Quantified variable.
          mkHeadTermVar(sym)
        } else {
          // Case 1.2: Lexically bound variable.
          mkHeadTermLit(box(exp0))
        }

      case _ =>
        // Compute the universally quantified variables (i.e. the variables not bound by the local scope).
        val quantifiedFreeVars = quantifiedVars(cparams0, exp0)

        if (quantifiedFreeVars.isEmpty) {
          // Case 2: No quantified variables. The expression can be reduced to a value.
          mkHeadTermLit(box(exp0))
        } else {
          // Case 3: Quantified variables. The expression is translated to an application term.
          mkAppTerm(quantifiedFreeVars, exp0, exp0.loc)
        }
    }
  }

  /**
    * Lowers the given body term `pat0`.
    */
  private def visitBodyTerm(cparams0: List[ConstraintParam], pat0: Pattern)(implicit root: Root, flix: Flix): Expression = pat0 match {
    case Pattern.Wild(_, loc) =>
      mkBodyTermWild(loc)

    case Pattern.Var(sym, tpe, loc) =>
      if (isQuantifiedVar(sym, cparams0)) {
        // Case 1: Quantified variable.
        mkBodyTermVar(sym)
      } else {
        // Case 2: Lexically bound variable *expression*.
        mkBodyTermLit(box(Expression.Var(sym, tpe, loc)))
      }

    case Pattern.Unit(loc) =>
      mkBodyTermLit(box(Expression.Unit(loc)))

    case Pattern.True(loc) =>
      mkBodyTermLit(box(Expression.True(loc)))

    case Pattern.False(loc) =>
      mkBodyTermLit(box(Expression.False(loc)))

    case Pattern.Char(lit, loc) =>
      mkBodyTermLit(box(Expression.Char(lit, loc)))

    case Pattern.Float32(lit, loc) =>
      mkBodyTermLit(box(Expression.Float32(lit, loc)))

    case Pattern.Float64(lit, loc) =>
      mkBodyTermLit(box(Expression.Float64(lit, loc)))

    case Pattern.BigDecimal(lit, loc) =>
      mkBodyTermLit(box(Expression.BigDecimal(lit, loc)))

    case Pattern.Int8(lit, loc) =>
      mkBodyTermLit(box(Expression.Int8(lit, loc)))

    case Pattern.Int16(lit, loc) =>
      mkBodyTermLit(box(Expression.Int16(lit, loc)))

    case Pattern.Int32(lit, loc) =>
      mkBodyTermLit(box(Expression.Int32(lit, loc)))

    case Pattern.Int64(lit, loc) =>
      mkBodyTermLit(box(Expression.Int64(lit, loc)))

    case Pattern.BigInt(lit, loc) =>
      mkBodyTermLit(box(Expression.BigInt(lit, loc)))

    case Pattern.Str(lit, loc) =>
      mkBodyTermLit(box(Expression.Str(lit, loc)))

    case Pattern.Tag(_, _, _, _) => throw InternalCompilerException(s"Unexpected pattern: '$pat0'.")

    case Pattern.Tuple(_, _, _) => throw InternalCompilerException(s"Unexpected pattern: '$pat0'.")

    case Pattern.Array(_, _, _) => throw InternalCompilerException(s"Unexpected pattern: '$pat0'.")

    case Pattern.ArrayTailSpread(_, _, _, _) => throw InternalCompilerException(s"Unexpected pattern: '$pat0'.")

    case Pattern.ArrayHeadSpread(_, _, _, _) => throw InternalCompilerException(s"Unexpected pattern: '$pat0'.")
  }

  /**
    * Lowers the given JvmMethod `method`.
    */
  private def visitJvmMethod(method: JvmMethod)(implicit root: Root, flix: Flix): JvmMethod = method match {
    case JvmMethod(ident, fparams, exp, retTyp, pur, eff, loc) =>
      val fs = fparams.map(visitFormalParam)
      val e = visitExp(exp)
      val t = visitType(retTyp)
      JvmMethod(ident, fs, e, t, pur, eff, loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.HeadTerm.Var` from the given variable symbol `sym`.
    */
  private def mkHeadTermVar(sym: Symbol.VarSym)(implicit root: Root, flix: Flix): Expression = {
    val innerExp = mkVarSym(sym)
    mkTag(Enums.HeadTerm, "Var", innerExp, Types.HeadTerm, sym.loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.HeadTerm.Lit` value which wraps the given expression `exp`.
    */
  private def mkHeadTermLit(exp: Expression)(implicit root: Root, flix: Flix): Expression = {
    mkTag(Enums.HeadTerm, "Lit", exp, Types.HeadTerm, exp.loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.BodyTerm.Wild` from the given source location `loc`.
    */
  private def mkBodyTermWild(loc: SourceLocation): Expression = {
    val innerExp = Expression.Unit(loc)
    mkTag(Enums.BodyTerm, "Wild", innerExp, Types.BodyTerm, loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.BodyTerm.Var` from the given variable symbol `sym`.
    */
  private def mkBodyTermVar(sym: Symbol.VarSym): Expression = {
    val innerExp = mkVarSym(sym)
    mkTag(Enums.BodyTerm, "Var", innerExp, Types.BodyTerm, sym.loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.BodyTerm.Lit` from the given expression `exp0`.
    */
  private def mkBodyTermLit(exp: Expression)(implicit root: Root, flix: Flix): Expression = {
    mkTag(Enums.BodyTerm, "Lit", exp, Types.BodyTerm, exp.loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.Denotation` from the given denotation `d` and type `tpeOpt`
    * (which must be the optional type of the last term).
    */
  private def mkDenotation(d: Denotation, tpeOpt: Option[Type], loc: SourceLocation)(implicit root: Root, flix: Flix): Expression = d match {
    case Relational =>
      val innerExp = Expression.Unit(loc)
      mkTag(Enums.Denotation, "Relational", innerExp, Types.Denotation, loc)

    case Latticenal =>
      tpeOpt match {
        case None => throw InternalCompilerException("Unexpected nullary lattice predicate.")
        case Some(tpe) =>
          // The type `Denotation[tpe]`.
          val unboxedDenotationType = Type.mkEnum(Enums.Denotation, tpe :: Nil, loc)

          // The type `Denotation[Boxed]`.
          val boxedDenotationType = Types.Denotation

          val Lattice: Symbol.DefnSym = Symbol.mkDefnSym("Fixpoint/Ast.lattice")
          val LatticeType: Type = Type.mkPureArrow(Type.Unit, unboxedDenotationType, loc)

          val Box: Symbol.DefnSym = Symbol.mkDefnSym("Fixpoint/Ast.box")
          val BoxType: Type = Type.mkPureArrow(unboxedDenotationType, boxedDenotationType, loc)

          val innerApply = Expression.Apply(Expression.Def(Lattice, LatticeType, loc), List(Expression.Unit(loc)), unboxedDenotationType, Type.Pure, Type.Empty, loc)
          Expression.Apply(Expression.Def(Box, BoxType, loc), List(innerApply), boxedDenotationType, Type.Pure, Type.Empty, loc)
      }
  }

  /**
    * Constructs a `Fixpoint/Ast.Polarity` from the given polarity `p`.
    */
  private def mkPolarity(p: Polarity, loc: SourceLocation): Expression = p match {
    case Polarity.Positive =>
      val innerExp = Expression.Unit(loc)
      mkTag(Enums.Polarity, "Positive", innerExp, Types.Polarity, loc)

    case Polarity.Negative =>
      val innerExp = Expression.Unit(loc)
      mkTag(Enums.Polarity, "Negative", innerExp, Types.Polarity, loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.Fixity` from the given fixity `f`.
    */
  private def mkFixity(f: Ast.Fixity, loc: SourceLocation): Expression = f match {
    case Fixity.Loose =>
      val innerExp = Expression.Unit(loc)
      mkTag(Enums.Fixity, "Loose", innerExp, Types.Fixity, loc)

    case Fixity.Fixed =>
      val innerExp = Expression.Unit(loc)
      mkTag(Enums.Fixity, "Fixed", innerExp, Types.Fixity, loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.PredSym` from the given predicate `pred`.
    */
  private def mkPredSym(pred: Name.Pred): Expression = pred match {
    case Name.Pred(sym, loc) =>
      val nameExp = Expression.Str(sym, loc)
      val idExp = Expression.Int64(0, loc)
      val inner = mkTuple(List(nameExp, idExp), loc)
      mkTag(Enums.PredSym, "PredSym", inner, Types.PredSym, loc)
  }

  /**
    * Constructs a `Fixpoint/Ast.VarSym` from the given variable symbol `sym`.
    */
  private def mkVarSym(sym: Symbol.VarSym): Expression = {
    val nameExp = Expression.Str(sym.text, sym.loc)
    mkTag(Enums.VarSym, "VarSym", nameExp, Types.VarSym, sym.loc)
  }

  /**
    * Returns the given expression `exp` in a box.
    */
  private def box(exp: Expression)(implicit root: Root, flix: Flix): Expression = {
    val loc = exp.loc
    val tpe = Type.mkPureArrow(exp.tpe, Types.Boxed, loc)
    val innerExp = Expression.Sig(Sigs.Box, tpe, loc)
    Expression.Apply(innerExp, List(exp), Types.Boxed, Type.Pure, Type.Empty, loc)
  }

  /**
    * Returns a `Fixpoint/Ast.BodyPredicate.GuardX`.
    *
    * mkGuard and mkAppTerm are similar and should probably be maintained together.
    */
  private def mkGuard(fvs: List[(Symbol.VarSym, Type)], exp: Expression, loc: SourceLocation)(implicit root: Root, flix: Flix): Expression = {
    // Compute the number of free variables.
    val arity = fvs.length

    // Check that we have <= 5 free variables.
    if (arity > 5) {
      throw InternalCompilerException("Cannot lift functions with more than 5 free variables.")
    }

    // Special case: No free variables.
    if (fvs.isEmpty) {
      val sym = Symbol.freshVarSym("_unit", BoundBy.FormalParam, loc)
      // Construct a lambda that takes the unit argument.
      val fparam = FormalParam(sym, Ast.Modifiers.Empty, Type.Unit, Ast.TypeSource.Ascribed, loc)
      val tpe = Type.mkPureArrow(Type.Unit, exp.tpe, loc)
      val lambdaExp = Expression.Lambda(fparam, exp, tpe, loc)
      return mkTag(Enums.BodyPredicate, s"Guard0", lambdaExp, Types.BodyPredicate, loc)
    }

    // Introduce a fresh variable for each free variable.
    val freshVars = fvs.foldLeft(Map.empty[Symbol.VarSym, Symbol.VarSym]) {
      case (acc, (oldSym, _)) => acc + (oldSym -> Symbol.freshVarSym(oldSym))
    }

    // Substitute every symbol in `exp` for its fresh equivalent.
    val freshExp = substExp(exp, freshVars)

    // Curry `freshExp` in a lambda expression for each free variable.
    val lambdaExp = fvs.foldRight(freshExp) {
      case ((oldSym, tpe), acc) =>
        val freshSym = freshVars(oldSym)
        val fparam = FormalParam(freshSym, Ast.Modifiers.Empty, tpe, Ast.TypeSource.Ascribed, loc)
        val lambdaType = Type.mkPureArrow(tpe, acc.tpe, loc)
        Expression.Lambda(fparam, acc, lambdaType, loc)
    }

    // Lift the lambda expression to operate on boxed values.
    val liftedExp = liftXb(lambdaExp, fvs.map(_._2))

    // Construct the `Fixpoint.Ast/BodyPredicate` value.
    val varExps = fvs.map(kv => mkVarSym(kv._1))
    val innerExp = mkTuple(liftedExp :: varExps, loc)
    mkTag(Enums.BodyPredicate, s"Guard$arity", innerExp, Types.BodyPredicate, loc)
  }

  /**
    * Returns a `Fixpoint/Ast.Term.AppX`.
    *
    * Note: mkGuard and mkAppTerm are similar and should probably be maintained together.
    */
  private def mkAppTerm(fvs: List[(Symbol.VarSym, Type)], exp: Expression, loc: SourceLocation)(implicit root: Root, flix: Flix): Expression = {
    // Compute the number of free variables.
    val arity = fvs.length

    // Check that we have <= 5 free variables.
    if (arity > 5) {
      throw InternalCompilerException("Cannot lift functions with more than 5 free variables.")
    }

    // Special case: No free variables.
    if (fvs.isEmpty) {
      val sym = Symbol.freshVarSym("_unit", BoundBy.FormalParam, loc)
      // Construct a lambda that takes the unit argument.
      val fparam = FormalParam(sym, Ast.Modifiers.Empty, Type.Unit, Ast.TypeSource.Ascribed, loc)
      val tpe = Type.mkPureArrow(Type.Unit, exp.tpe, loc)
      val lambdaExp = Expression.Lambda(fparam, exp, tpe, loc)
      return mkTag(Enums.HeadTerm, s"App0", lambdaExp, Types.HeadTerm, loc)
    }

    // Introduce a fresh variable for each free variable.
    val freshVars = fvs.foldLeft(Map.empty[Symbol.VarSym, Symbol.VarSym]) {
      case (acc, (oldSym, _)) => acc + (oldSym -> Symbol.freshVarSym(oldSym))
    }

    // Substitute every symbol in `exp` for its fresh equivalent.
    val freshExp = substExp(exp, freshVars)

    // Curry `freshExp` in a lambda expression for each free variable.
    val lambdaExp = fvs.foldRight(freshExp) {
      case ((oldSym, tpe), acc) =>
        val freshSym = freshVars(oldSym)
        val fparam = FormalParam(freshSym, Ast.Modifiers.Empty, tpe, Ast.TypeSource.Ascribed, loc)
        val lambdaType = Type.mkPureArrow(tpe, acc.tpe, loc)
        Expression.Lambda(fparam, acc, lambdaType, loc)
    }

    // Lift the lambda expression to operate on boxed values.
    val liftedExp = liftX(lambdaExp, fvs.map(_._2), exp.tpe)

    // Construct the `Fixpoint.Ast/BodyPredicate` value.
    val varExps = fvs.map(kv => mkVarSym(kv._1))
    val innerExp = mkTuple(liftedExp :: varExps, loc)
    mkTag(Enums.HeadTerm, s"App$arity", innerExp, Types.HeadTerm, loc)
  }

  /**
    * Make a new channel expression
    */
  private def mkNewChannel(exp: Expression, tpe: Type, pur: Type, eff: Type, loc: SourceLocation): Expression = {
    val newChannel = Expression.Def(Defs.ChannelNew, Type.mkImpureArrow(exp.tpe, tpe, loc), loc)
    Expression.Apply(newChannel, exp :: Nil, tpe, pur, eff, loc)
  }

  /**
    * Make a channel get expression
    */
  private def mkGetChannel(exp: Expression, tpe: Type, pur: Type, eff: Type, loc: SourceLocation): Expression = {
    val getChannel = Expression.Def(Defs.ChannelGet, Type.mkImpureArrow(exp.tpe, tpe, loc), loc)
    Expression.Apply(getChannel, exp :: Nil, tpe, pur, eff, loc)
  }

  /**
    * Make a channel put expression
    */
  private def mkPutChannel(exp1: Expression, exp2: Expression, pur: Type, eff: Type, loc: SourceLocation): Expression = {
    val putChannel = Expression.Def(Defs.ChannelPut, Type.mkImpureUncurriedArrow(List(exp2.tpe, exp1.tpe), Type.Unit, loc), loc)
    Expression.Apply(putChannel, List(exp2, exp1), Type.Unit, pur, eff, loc)
  }

  /**
    * Make the array of MpmcAdmin objects which will be passed to `selectFrom`
    */
  private def mkChannelAdminArray(rs: List[SelectChannelRule], channels: List[(Symbol.VarSym, Expression)], loc: SourceLocation): Expression = {
    val admins = rs.zip(channels) map {
      case (SelectChannelRule(_, c, _), (chanSym, _)) =>
        val admin = Expression.Def(Defs.ChannelMpmcAdmin, Type.mkPureArrow(c.tpe, Types.ChannelMpmcAdmin, loc), loc)
        Expression.Apply(admin, List(Expression.Var(chanSym, c.tpe, loc)), Types.ChannelMpmcAdmin, Type.Pure, Type.Empty, loc)
    }
    mkArray(admins, Types.ChannelMpmcAdmin, loc)
  }

  /**
    * Construct a call to `selectFrom` given an array of MpmcAdmin objects and optional default
    */
  private def mkChannelSelect(adminArray: Expression, default: Option[Expression], loc: SourceLocation): Expression = {
    val locksType = Types.mkList(Types.ConcurrentReentrantLock, loc)

    val selectRetTpe = Type.mkTuple(List(Type.Int32, locksType), loc)
    val selectTpe = Type.mkImpureUncurriedArrow(List(adminArray.tpe, Type.Bool), selectRetTpe, loc)
    val select = Expression.Def(Defs.ChannelSelectFrom, selectTpe, loc)
    val blocking = default match {
      case Some(_) => Expression.False(loc)
      case None => Expression.True(loc)
    }
    Expression.Apply(select, List(adminArray, blocking), selectRetTpe, Type.Impure, Type.Empty, loc)
  }

  /**
    * Construct a sequence of MatchRules corresponding to the given SelectChannelRules
    */
  private def mkChannelCases(rs: List[SelectChannelRule], channels: List[(Symbol.VarSym, Expression)], pur: Type, eff: Type, loc: SourceLocation)(implicit flix: Flix): List[MatchRule] = {
    val locksType = Types.mkList(Types.ConcurrentReentrantLock, loc)

    rs.zip(channels).zipWithIndex map {
      case ((SelectChannelRule(sym, chan, exp), (chSym, _)), i) =>
        val locksSym = mkLetSym("locks", loc)
        val pat = mkTuplePattern(List(Pattern.Int32(i, loc), Pattern.Var(locksSym, locksType, loc)), loc)
        val getTpe = Type.eraseTopAliases(chan.tpe) match {
          case Type.Apply(_, t, _) => t
          case _ => throw InternalCompilerException("Unexpected channel type found.")
        }
        val get = Expression.Def(Defs.ChannelUnsafeGetAndUnlock, Type.mkImpureUncurriedArrow(List(chan.tpe, locksType), getTpe, loc), loc)
        val getExp = Expression.Apply(get, List(Expression.Var(chSym, chan.tpe, loc), Expression.Var(locksSym, locksType, loc)), getTpe, pur, eff, loc)
        val e = Expression.Let(sym, Ast.Modifiers.Empty, getExp, exp, exp.tpe, pur, eff, loc)
        MatchRule(pat, None, e)
    }
  }

  /**
    * Construct additional MatchRule to handle the (optional) default case
    * NB: Does not need to unlock because that is handled inside Concurrent/Channel.selectFrom.
    */
  private def mkSelectDefaultCase(default: Option[Expression], t: Type, loc: SourceLocation)(implicit flix: Flix): List[MatchRule] = {
    default match {
      case Some(defaultExp) =>
        val pat = mkTuplePattern(List(Pattern.Int32(-1, loc), mkWildPattern(loc)), loc)
        val defaultMatch = MatchRule(pat, None, defaultExp)
        List(defaultMatch)
      case _ =>
        List()
    }
  }

  /**
    * Lifts the given lambda expression `exp0` with the given argument types `argTypes`.
    *
    * Note: liftX and liftXb are similar and should probably be maintained together.
    */
  private def liftX(exp0: Expression, argTypes: List[Type], resultType: Type): Expression = {
    // Compute the liftXb symbol.
    val sym = Symbol.mkDefnSym(s"Boxable.lift${argTypes.length}")

    //
    // The liftX family of functions are of the form: a -> b -> c -> `resultType` and
    // returns a function of the form Boxed -> Boxed -> Boxed -> Boxed -> Boxed`.
    // That is, the function accepts a *curried* function and returns a *curried* function.
    //

    // The type of the function argument, i.e. a -> b -> c -> `resultType`.
    val argType = Type.mkPureCurriedArrow(argTypes, resultType, exp0.loc)

    // The type of the returned function, i.e. Boxed -> Boxed -> Boxed -> Boxed.
    val returnType = Type.mkPureCurriedArrow(argTypes.map(_ => Types.Boxed), Types.Boxed, exp0.loc)

    // The type of the overall liftX function, i.e. (a -> b -> c -> `resultType`) -> (Boxed -> Boxed -> Boxed -> Boxed).
    val liftType = Type.mkPureArrow(argType, returnType, exp0.loc)

    // Construct a call to the liftX function.
    val defn = Expression.Def(sym, liftType, exp0.loc)
    Expression.Apply(defn, List(exp0), returnType, Type.Pure, Type.Empty, exp0.loc)
  }

  /**
    * Lifts the given Boolean-valued lambda expression `exp0` with the given argument types `argTypes`.
    *
    * Note: liftX and liftXb are similar and should probably be maintained together.
    */
  private def liftXb(exp0: Expression, argTypes: List[Type]): Expression = {
    // Compute the liftXb symbol.
    val sym = Symbol.mkDefnSym(s"Boxable.lift${argTypes.length}b")

    //
    // The liftX family of functions are of the form: a -> b -> c -> Bool and
    // returns a function of the form Boxed -> Boxed -> Boxed -> Boxed -> Bool.
    // That is, the function accepts a *curried* function and returns a *curried* function.
    //

    // The type of the function argument, i.e. a -> b -> c -> Bool.
    val argType = Type.mkPureCurriedArrow(argTypes, Type.Bool, exp0.loc)

    // The type of the returned function, i.e. Boxed -> Boxed -> Boxed -> Bool.
    val returnType = Type.mkPureCurriedArrow(argTypes.map(_ => Types.Boxed), Type.Bool, exp0.loc)

    // The type of the overall liftXb function, i.e. (a -> b -> c -> Bool) -> (Boxed -> Boxed -> Boxed -> Bool).
    val liftType = Type.mkPureArrow(argType, returnType, exp0.loc)

    // Construct a call to the liftXb function.
    val defn = Expression.Def(sym, liftType, exp0.loc)
    Expression.Apply(defn, List(exp0), returnType, Type.Pure, Type.Empty, exp0.loc)
  }

  /**
    * Returns a pure array expression constructed from the given list of expressions `exps`.
    */
  private def mkArray(exps: List[Expression], elmType: Type, loc: SourceLocation): Expression = {
    val tpe = Type.mkArray(elmType, Type.Pure, loc)
    val pur = Type.Pure
    val eff = Type.Empty
    val reg = Expression.Unit(loc)
    Expression.ArrayLit(exps, reg, tpe, pur, eff, loc)
  }

  /**
    * Returns a list expression constructed from the given `exps` with type list of `elmType`.
    */
  private def mkList(exps: List[Expression], elmType: Type, loc: SourceLocation): Expression = {
    val nil = mkNil(elmType, loc)
    exps.foldRight(nil) {
      case (e, acc) => mkCons(e, acc, loc)
    }
  }

  /**
    * Returns a `Nil` expression with type list of `elmType`.
    */
  private def mkNil(elmType: Type, loc: SourceLocation): Expression = {
    mkTag(Enums.FList, "Nil", Expression.Unit(loc), Types.mkList(elmType, loc), loc)
  }

  /**
    * returns a `Cons(hd, tail)` expression with type `tail.tpe`.
    */
  private def mkCons(hd: Expression, tail: Expression, loc: SourceLocation): Expression = {
    val tuple = mkTuple(hd :: tail :: Nil, loc)
    mkTag(Enums.FList, "Cons", tuple, tail.tpe, loc)
  }

  /**
    * Returns a pure tag expression for the given `sym` and given `tag` with the given inner expression `exp`.
    */
  private def mkTag(sym: Symbol.EnumSym, tag: String, exp: Expression, tpe: Type, loc: SourceLocation): Expression = {
    val caseSym = new Symbol.CaseSym(sym, tag, SourceLocation.Unknown)
    Expression.Tag(Ast.CaseSymUse(caseSym, loc), exp, tpe, Type.Pure, Type.Empty, loc)
  }

  /**
    * Returns a pure tuple expression constructed from the given list of expressions `exps`.
    */
  private def mkTuple(exps: List[Expression], loc: SourceLocation): Expression = {
    val tpe = Type.mkTuple(exps.map(_.tpe), loc)
    val pur = Type.Pure
    val eff = Type.Empty
    Expression.Tuple(exps, tpe, pur, eff, loc)
  }

  /**
    * Returns a new `VarSym` for use in a let-binding.
    *
    * This function is called `mkLetSym` to avoid confusion with [[mkVarSym]].
    */
  private def mkLetSym(prefix: String, loc: SourceLocation)(implicit flix: Flix): Symbol.VarSym = {
    val name = prefix + Flix.Delimiter + flix.genSym.freshId()
    Symbol.freshVarSym(name, BoundBy.Let, loc)
  }

  /**
    * The type of a channel which can transmit variables of type `tpe`
    */
  private def mkChannelTpe(tpe: Type, loc: SourceLocation): Type = {
    Type.Apply(Type.Cst(TypeConstructor.Enum(Enums.ChannelMpmc, Kind.Star ->: Kind.Star), loc), tpe, loc)
  }

  /**
    * An expression for a channel variable called `sym`
    */
  private def mkChannelExp(sym: Symbol.VarSym, tpe: Type, loc: SourceLocation): Expression = {
    Expression.Var(sym, mkChannelTpe(tpe, loc), loc)
  }

  /**
    * Returns a list of `GetChannel` expressions based on `symExps`.
    */
  private def mkParWaits(symExps: List[(Symbol.VarSym, Expression)]): List[Expression] = {
    // Make wait expressions `<- ch, ..., <- chn`.
    symExps.map {
      case (sym, e) =>
        val loc = e.loc.asSynthetic
        val chExp = mkChannelExp(sym, e.tpe, loc)
        mkGetChannel(chExp, e.tpe, Type.Impure, e.eff, loc)
    }
  }

  /**
    * Returns a full `par exp` expression.
    */
  private def mkParChannels(exp: Expression, chanSymsWithExps: List[(Symbol.VarSym, Expression)]): Expression = {
    // Make spawn expressions `spawn ch <- exp`.
    val spawns = chanSymsWithExps.foldRight(exp: Expression) {
      case ((sym, e), acc) =>
        val loc = e.loc.asSynthetic
        val e1 = mkChannelExp(sym, e.tpe, loc) // The channel `ch`
        val e2 = mkPutChannel(e1, e, Type.Impure, Type.mkUnion(e.eff, e1.eff, loc), loc) // The put exp: `ch <- exp0`.
        val e3 = Expression.Spawn(e2, Type.Unit, Type.Impure, e2.eff, loc) // Spawn the put expression from above i.e. `spawn ch <- exp0`.
        Expression.Stm(e3, acc, e1.tpe, Type.mkAnd(e3.pur, acc.pur, loc), Type.mkUnion(e3.eff, acc.eff, loc), loc) // Return a statement expression containing the other spawn expressions along with this one.
    }

    // Make let bindings `let ch = chan 1;`.
    chanSymsWithExps.foldRight(spawns: Expression) {
      case ((sym, e), acc) =>
        val loc = e.loc.asSynthetic
        val chan = mkNewChannel(Expression.Int32(1, loc), mkChannelTpe(e.tpe, loc), Type.Impure, Type.Empty, loc) // The channel exp `chan 1`
        Expression.Let(sym, Modifiers(List(Ast.Modifier.Synthetic)), chan, acc, acc.tpe, Type.mkAnd(e.pur, acc.pur, loc), Type.mkUnion(e.eff, acc.eff, loc), loc) // The let-binding `let ch = chan 1`
    }
  }

  /**
    * Returns a desugared let-match expression, i.e.
    * {{{
    *   let pattern = exp;
    *   body
    * }}}
    * is desugared to
    * {{{
    *   match exp {
    *     case pattern => body
    *   }
    * }}}
    */
  def mkLetMatch(exp: Expression, pat: Pattern, body: Expression): Expression = {
    val expLoc = exp.loc.asSynthetic
    val rule = List(MatchRule(pat, None, body))
    val pur = Type.mkAnd(exp.pur, body.pur, expLoc)
    val eff = Type.mkUnion(exp.eff, body.eff, expLoc)
    Expression.Match(exp, rule, body.tpe, pur, eff, expLoc)
  }

  /**
    * Returns an expression where the pattern variables used in `exp` are
    * bound to [[Expression.GetChannel]] expressions,
    * i.e.
    * {{{
    *   let pat1 = <- ch1;
    *   let pat2 = <- ch2;
    *   let pat3 = <- ch3;
    *   ...
    *   let patn = <- chn;
    *   exp
    * }}}
    */
  def mkBoundParWaits(patSymExps: List[(Pattern, Symbol.VarSym, Expression)], exp: Expression): Expression =
    patSymExps.map {
      case (p, sym, e) =>
        val loc = e.loc.asSynthetic
        val chExp = mkChannelExp(sym, e.tpe, loc)
        (p, mkGetChannel(chExp, e.tpe, Type.Impure, e.eff, loc))
    }.foldRight(exp) {
      case ((pat, chan), e) => mkLetMatch(chan, pat, e)
    }

  /**
    * Returns a desugared [[Expression.ParYield]] expression.
    * The parameter `exp` should already have its patterns
    * and expressions visited by the [[visitPat]] and [[visitExp]] function respectively.
    */
  def mkParYield(parYieldExp: Expression.ParYield)(implicit flix: Flix): Expression = {
    // Generate symbols for each channel.
    val chanSymsWithPatAndExp = parYieldExp.frags.map { case ParYieldFragment(p, e, l) => (p, mkLetSym("channel", l.asSynthetic), e) }
    val desugaredYieldExp = mkBoundParWaits(chanSymsWithPatAndExp, parYieldExp.exp)
    val chanSymsWithExp = chanSymsWithPatAndExp.map { case (_, s, e) => (s, e) }
    val blockExp = mkParChannels(desugaredYieldExp, chanSymsWithExp)
    Expression.Cast(blockExp, None, Some(Type.Pure), Some(Type.Empty), parYieldExp.tpe, parYieldExp.pur, parYieldExp.eff, parYieldExp.loc.asSynthetic)
  }

  /**
    * Returns a tuple expression that is evaluated in parallel.
    *
    * {{{
    *   par (exp0, exp1, exp2)
    * }}}
    *
    * is translated to
    *
    * {{{
    *   let ch0 = chan 1;
    *   let ch1 = chan 1;
    *   let ch2 = chan 1;
    *   spawn ch0 <- exp0;
    *   spawn ch1 <- exp1;
    *   spawn ch2 <- exp2;
    *   (<- ch0, <- ch1, <- ch2)
    * }}}
    */
  private def mkParTuple(exp: Expression.Tuple)(implicit flix: Flix): Expression = {
    val Expression.Tuple(elms, tpe, pur, eff, loc) = exp

    // Generate symbols for each channel.
    val chanSymsWithExps = elms.map(e => (mkLetSym("channel", e.loc.asSynthetic), e))

    val waitExps = mkParWaits(chanSymsWithExps)
    val tuple = Expression.Tuple(waitExps, tpe, pur, eff, loc.asSynthetic)
    mkParChannels(tuple, chanSymsWithExps)
  }

  /**
    * Applies the given expression `exp` to the `debug` function.
    */
  private def mkApplyDebug(exp1: Expression, exp2: Expression, loc: SourceLocation)(implicit root: Root, flix: Flix): Expression = {
    //
    // Note that we mark the call as impure (even though it may have been typed as pure!)
    //
    val tpe = Type.mkImpureUncurriedArrow(exp1.tpe :: exp2.tpe :: Nil, exp2.tpe, loc)
    val innerExp = Expression.Def(Defs.DebugWithPrefix, tpe, loc)
    Expression.Apply(innerExp, exp1 :: exp2 :: Nil, exp2.tpe, Type.Impure, Type.Empty, loc)
  }

  /**
    * Returns a Pattern representing a tuple of patterns.
    */
  def mkTuplePattern(patterns: List[Pattern], loc: SourceLocation): Pattern = {
    Pattern.Tuple(patterns, Type.mkTuple(patterns.map(_.tpe), loc), loc)
  }

  /**
    * Returns a wilcard (match anything) pattern.
    */
  def mkWildPattern(loc: SourceLocation)(implicit flix: Flix): Pattern = {
    Pattern.Wild(Type.freshVar(Kind.Star, loc, text = Ast.VarText.FallbackText("wild")), loc)
  }

  /**
    * Return a list of quantified variables in the given expression `exp0`.
    *
    * A variable is quantified (i.e. *NOT* lexically bound) if it occurs in the expression `exp0`
    * but not in the constraint params `cparams0` of the constraint.
    */
  private def quantifiedVars(cparams0: List[ConstraintParam], exp0: Expression): List[(Symbol.VarSym, Type)] = {
    TypedAstOps.freeVars(exp0).toList.filter {
      case (sym, _) => isQuantifiedVar(sym, cparams0)
    }
  }

  /**
    * Returns `true` if the given variable symbol `sym` is a quantified variable according to the given constraint params `cparams0`.
    *
    * That is, the variable symbol is *NOT* lexically bound.
    */
  private def isQuantifiedVar(sym: Symbol.VarSym, cparams0: List[ConstraintParam]): Boolean =
    cparams0.exists(p => p.sym == sym)


  // TODO: Move into TypedAstOps

  /**
    * Applies the given substitution `subst` to the given expression `exp0`.
    */
  private def substExp(exp0: Expression, subst: Map[Symbol.VarSym, Symbol.VarSym]): Expression = exp0 match {
    case Expression.Unit(_) => exp0

    case Expression.Null(_, _) => exp0

    case Expression.True(_) => exp0

    case Expression.False(_) => exp0

    case Expression.Char(_, _) => exp0

    case Expression.Float32(_, _) => exp0

    case Expression.Float64(_, _) => exp0

    case Expression.BigDecimal(_, _) => exp0

    case Expression.Int8(_, _) => exp0

    case Expression.Int16(_, _) => exp0

    case Expression.Int32(_, _) => exp0

    case Expression.Int64(_, _) => exp0

    case Expression.BigInt(_, _) => exp0

    case Expression.Str(_, _) => exp0

    case Expression.Wild(_, _) => exp0

    case Expression.Var(sym, tpe, loc) =>
      val s = subst.getOrElse(sym, sym)
      Expression.Var(s, tpe, loc)

    case Expression.Def(_, _, _) => exp0

    case Expression.Sig(_, _, _) => exp0

    case Expression.Hole(_, _, _) => exp0

    case Expression.Lambda(fparam, exp, tpe, loc) =>
      val p = substFormalParam(fparam, subst)
      val e = substExp(exp, subst)
      Expression.Lambda(p, e, tpe, loc)

    case Expression.Apply(exp, exps, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      val es = exps.map(substExp(_, subst))
      Expression.Apply(e, es, tpe, pur, eff, loc)

    case Expression.Unary(sop, exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Unary(sop, e, tpe, pur, eff, loc)

    case Expression.Binary(sop, exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.Binary(sop, e1, e2, tpe, pur, eff, loc)

    case Expression.Let(sym, mod, exp1, exp2, tpe, pur, eff, loc) =>
      val s = subst.getOrElse(sym, sym)
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.Let(s, mod, e1, e2, tpe, pur, eff, loc)

    case Expression.LetRec(sym, mod, exp1, exp2, tpe, pur, eff, loc) =>
      val s = subst.getOrElse(sym, sym)
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.LetRec(s, mod, e1, e2, tpe, pur, eff, loc)

    case Expression.Region(tpe, loc) =>
      Expression.Region(tpe, loc)

    case Expression.Scope(sym, regionVar, exp, tpe, pur, eff, loc) =>
      val s = subst.getOrElse(sym, sym)
      val e = substExp(exp, subst)
      Expression.Scope(s, regionVar, e, tpe, pur, eff, loc)

    case Expression.IfThenElse(exp1, exp2, exp3, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      val e3 = substExp(exp3, subst)
      Expression.IfThenElse(e1, e2, e3, tpe, pur, eff, loc)

    case Expression.Stm(exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.Stm(e1, e2, tpe, pur, eff, loc)

    case Expression.Discard(exp, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Discard(e, pur, eff, loc)

    case Expression.Match(_, _, _, _, _, _) => ??? // TODO

    case Expression.TypeMatch(_, _, _, _, _, _) => ??? // TODO

    case Expression.Choose(exps, rules, tpe, pur, eff, loc) =>
      val es = exps.map(substExp(_, subst))
      val rs = rules map {
        case ChoiceRule(pat, exp) =>
          // TODO: Substitute in patterns?
          ChoiceRule(pat, substExp(exp, subst))
      }
      Expression.Choose(es, rs, tpe, pur, eff, loc)

    case Expression.Tag(sym, exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Tag(sym, e, tpe, pur, eff, loc)

    case Expression.Tuple(elms, tpe, pur, eff, loc) =>
      val es = elms.map(substExp(_, subst))
      Expression.Tuple(es, tpe, pur, eff, loc)

    case Expression.RecordEmpty(_, _) => exp0

    case Expression.RecordSelect(exp, field, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.RecordSelect(e, field, tpe, pur, eff, loc)

    case Expression.RecordExtend(field, value, rest, tpe, pur, eff, loc) =>
      val v = substExp(value, subst)
      val r = substExp(rest, subst)
      Expression.RecordExtend(field, v, r, tpe, pur, eff, loc)

    case Expression.RecordRestrict(field, rest, tpe, pur, eff, loc) =>
      val r = substExp(rest, subst)
      Expression.RecordRestrict(field, r, tpe, pur, eff, loc)

    case Expression.ArrayLit(exps, exp, tpe, pur, eff, loc) =>
      val es = exps.map(substExp(_, subst))
      val e = substExp(exp, subst)
      Expression.ArrayLit(es, e, tpe, pur, eff, loc)

    case Expression.ArrayNew(exp1, exp2, exp3, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      val e3 = substExp(exp3, subst)
      Expression.ArrayNew(e1, e2, e3, tpe, pur, eff, loc)

    case Expression.ArrayLoad(base, index, tpe, pur, eff, loc) =>
      val b = substExp(base, subst)
      val i = substExp(index, subst)
      Expression.ArrayLoad(b, i, tpe, pur, eff, loc)

    case Expression.ArrayLength(base, pur, eff, loc) =>
      val b = substExp(base, subst)
      Expression.ArrayLength(b, pur, eff, loc)

    case Expression.ArrayStore(base, index, elm, pur, eff, loc) =>
      val b = substExp(base, subst)
      val i = substExp(index, subst)
      Expression.ArrayStore(b, i, elm, pur, eff, loc)

    case Expression.ArraySlice(base, beginIndex, endIndex, tpe, pur, eff, loc) =>
      val b = substExp(base, subst)
      val bi = substExp(beginIndex, subst)
      val ei = substExp(endIndex, subst)
      Expression.ArraySlice(b, bi, ei, tpe, pur, eff, loc)

    case Expression.Ref(exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.Ref(e1, e2, tpe, pur, eff, loc)

    case Expression.Deref(exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Deref(e, tpe, pur, eff, loc)

    case Expression.Assign(exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.Assign(e1, e2, tpe, pur, eff, loc)

    case Expression.Ascribe(exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Ascribe(e, tpe, pur, eff, loc)

    case Expression.Cast(exp, declaredType, declaredPur, declaredEff, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Cast(e, declaredType, declaredPur, declaredEff, tpe, pur, eff, loc)

    case Expression.Mask(exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Mask(e, tpe, pur, eff, loc)

    case Expression.Upcast(exp, tpe, loc) =>
      Expression.Upcast(substExp(exp, subst), tpe, loc)

    case Expression.Without(exp, sym, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Without(e, sym, tpe, pur, eff, loc)

    case Expression.TryCatch(_, _, _, _, _, _) => ??? // TODO

    case Expression.TryWith(exp, sym, rules, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      val rs = rules.map {
        case HandlerRule(op, fparams, hexp) =>
          val fps = fparams.map(substFormalParam(_, subst))
          val he = substExp(hexp, subst)
          HandlerRule(op, fps, he)
      }
      Expression.TryWith(e, sym, rs, tpe, pur, eff, loc)

    case Expression.Do(sym, exps, pur, eff, loc) =>
      val es = exps.map(substExp(_, subst))
      Expression.Do(sym, es, pur, eff, loc)

    case Expression.Resume(exp, tpe, loc) =>
      val e = substExp(exp, subst)
      Expression.Resume(e, tpe, loc)

    case Expression.InvokeConstructor(constructor, args, tpe, pur, eff, loc) =>
      val as = args.map(substExp(_, subst))
      Expression.InvokeConstructor(constructor, as, tpe, pur, eff, loc)

    case Expression.InvokeMethod(method, exp, args, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      val as = args.map(substExp(_, subst))
      Expression.InvokeMethod(method, e, as, tpe, pur, eff, loc)

    case Expression.InvokeStaticMethod(method, args, tpe, pur, eff, loc) =>
      val as = args.map(substExp(_, subst))
      Expression.InvokeStaticMethod(method, as, tpe, pur, eff, loc)

    case Expression.GetField(field, exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.GetField(field, e, tpe, pur, eff, loc)

    case Expression.PutField(field, exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.PutField(field, e1, e2, tpe, pur, eff, loc)

    case Expression.GetStaticField(_, _, _, _, _) => exp0

    case Expression.PutStaticField(field, exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.PutStaticField(field, e, tpe, pur, eff, loc)

    case Expression.NewObject(_, _, _, _, _, _, _) => exp0

    case Expression.NewChannel(exp, tpe, elmTpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.NewChannel(e, tpe, elmTpe, pur, eff, loc)

    case Expression.GetChannel(exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.GetChannel(e, tpe, pur, eff, loc)

    case Expression.PutChannel(exp1, exp2, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.PutChannel(e1, e2, tpe, pur, eff, loc)

    case Expression.SelectChannel(_, _, _, _, _, _) => ??? // TODO

    case Expression.Spawn(exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Spawn(e, tpe, pur, eff, loc)

    case Expression.Par(exp, loc) =>
      Expression.Par(substExp(exp, subst), loc)

    case Expression.ParYield(frags, exp, tpe, pur, eff, loc) =>
      val fs = frags map {
        case ParYieldFragment(p, e, l) =>
          ParYieldFragment(p, substExp(e, subst), l)
      }
      val e = substExp(exp, subst)
      Expression.ParYield(fs, e, tpe, pur, eff, loc)

    case Expression.Lazy(exp, tpe, loc) =>
      val e = substExp(exp, subst)
      Expression.Lazy(e, tpe, loc)

    case Expression.Force(exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.Force(e, tpe, pur, eff, loc)

    case Expression.FixpointLambda(pparams, exp, stf, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.FixpointLambda(pparams, e, stf, tpe, pur, eff, loc)

    case Expression.FixpointMerge(exp1, exp2, stf, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      Expression.FixpointMerge(e1, e2, stf, tpe, pur, eff, loc)

    case Expression.FixpointSolve(exp, stf, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.FixpointSolve(e, stf, tpe, pur, eff, loc)

    case Expression.FixpointFilter(pred, exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.FixpointFilter(pred, e, tpe, pur, eff, loc)

    case Expression.FixpointInject(exp, pred, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.FixpointInject(e, pred, tpe, pur, eff, loc)

    case Expression.FixpointProject(pred, exp, tpe, pur, eff, loc) =>
      val e = substExp(exp, subst)
      Expression.FixpointProject(pred, e, tpe, pur, eff, loc)

    case Expression.Reify(t, tpe, pur, eff, loc) =>
      Expression.Reify(t, tpe, pur, eff, loc)

    case Expression.ReifyType(t, k, tpe, pur, eff, loc) =>
      Expression.ReifyType(t, k, tpe, pur, eff, loc)

    case Expression.ReifyEff(sym, exp1, exp2, exp3, tpe, pur, eff, loc) =>
      val e1 = substExp(exp1, subst)
      val e2 = substExp(exp2, subst)
      val e3 = substExp(exp3, subst)
      Expression.ReifyEff(sym, e1, e2, e3, tpe, pur, eff, loc)

    case Expression.FixpointConstraintSet(_, _, _, loc) => throw InternalCompilerException(s"Unexpected expression near ${loc.format}.")

  }

  /**
    * Applies the given substitution `subst` to the given formal param `fparam0`.
    */
  private def substFormalParam(fparam0: FormalParam, subst: Map[Symbol.VarSym, Symbol.VarSym]): FormalParam = fparam0 match {
    case FormalParam(sym, mod, tpe, src, loc) =>
      val s = subst.getOrElse(sym, sym)
      FormalParam(s, mod, tpe, src, loc)
  }

}
