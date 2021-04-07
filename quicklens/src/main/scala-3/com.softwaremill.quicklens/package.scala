package com.softwaremill

import scala.annotation.compileTimeOnly
import scala.quoted.*

package object quicklens {

  trait ModifyPimp {
    extension [S, A](inline obj: S)
      /**
        * Create an object allowing modifying the given (deeply nested) field accessible in a `case class` hierarchy
        * via `path` on the given `obj`.
        *
        * All modifications are side-effect free and create copies of the original objects.
        *
        * You can use `.each` to traverse options, lists, etc.
        */
      inline def modify(inline path: S => A): PathModify[S, A] = ${ modifyImpl('obj, 'path) }
      /**
        * Create an object allowing modifying the given (deeply nested) fields accessible in a `case class` hierarchy
        * via `paths` on the given `obj`.
        *
        * All modifications are side-effect free and create copies of the original objects.
        *
        * You can use `.each` to traverse options, lists, etc.
        */
      inline def modifyAll(inline path: S => A, inline paths: (S => A)*) : PathModify[S, A] = ${ modifyAllImpl('obj, 'path, 'paths) }
  }

  given modifyPimp: ModifyPimp with {}

  /**
    * Create an object allowing modifying the given (deeply nested) field accessible in a `case class` hierarchy
    * via `path` on the given `obj`.
    *
    * All modifications are side-effect free and create copies of the original objects.
    *
    * You can use `.each` to traverse options, lists, etc.
    */
  inline def modify[S, A](inline obj: S)(inline path: S => A): PathModify[S, A] =${ modifyImpl('obj, 'path) }

  /**
    * Create an object allowing modifying the given (deeply nested) fields accessible in a `case class` hierarchy
    * via `paths` on the given `obj`.
    *
    * All modifications are side-effect free and create copies of the original objects.
    *
    * You can use `.each` to traverse options, lists, etc.
    */
  inline def modifyAll[S, A](inline obj: S)(inline path: S => A, inline paths: (S => A)*) : PathModify[S, A] = ${ modifyAllImpl('obj, 'path, 'paths) }

  case class PathModify[S, A](obj: S, f: (A => A) => S) {
    
    /**
      * Transform the value of the field(s) using the given function.
      *
      * @return A copy of the root object with the (deeply nested) field(s) modified.
      */
    def using(mod: A => A): S = f.apply(mod)

    /** An alias for [[using]]. Explicit calls to [[using]] are preferred over this alias, but quicklens provides
      * this option because code auto-formatters (like scalafmt) will generally not keep [[modify]]/[[using]]
      * pairs on the same line, leading to code like
      * {{{
      * x
      *   .modify(_.foo)
      *   .using(newFoo :: _)
      *   .modify(_.bar)
      *   .using(_ + newBar)
      * }}}
      * When using [[apply]], scalafmt will allow
      * {{{
      * x
      *   .modify(_.foo)(newFoo :: _)
      *   .modify(_.bar)(_ + newBar)
      * }}}
      * */
    def apply(mod: A => A): S = using(mod)

    /**
      * Transform the value of the field(s) using the given function, if the condition is true. Otherwise, returns the
      * original object unchanged.
      *
      * @return A copy of the root object with the (deeply nested) field(s) modified, if `condition` is true.
      */
    def usingIf(condition: Boolean)(mod: A => A): S = if condition then using(mod) else obj

    /**
      * Set the value of the field(s) to a new value.
      *
      * @return A copy of the root object with the (deeply nested) field(s) set to the new value.
      */
    def setTo(v: A): S = f.apply(Function.const(v))

    /**
        * Set the value of the field(s) to a new value, if it is defined. Otherwise, returns the original object
        * unchanged.
        *
        * @return A copy of the root object with the (deeply nested) field(s) set to the new value, if it is defined.
        */
    def setToIfDefined(v: Option[A]): S = v.fold(obj)(setTo)

    /**
      * Set the value of the field(s) to a new value, if the condition is true. Otherwise, returns the original object
      * unchanged.
      *
      * @return A copy of the root object with the (deeply nested) field(s) set to the new value, if `condition` is
      *         true.
      */
    def setToIf(condition: Boolean)(v: A): S = if condition then setTo(v) else obj
  }

  def modifyLens[T]: LensHelper[T] = LensHelper[T]()
  def modifyAllLens[T]: MultiLensHelper[T] = MultiLensHelper[T]()

  case class LensHelper[T] private[quicklens] () {
    inline def apply[U](path: T => U): PathLazyModify[T, U] =  
      ${ '{PathLazyModify((t, mod) => ${modifyImpl('t, 'path)}.using(mod))} }
  }

  case class MultiLensHelper[T] private[quicklens] () {
    inline def apply[U](path1: T => U, paths: (T => U)*): PathLazyModify[T, U] =
      ${ '{PathLazyModify((t, mod) => ${modifyAllImpl('t, 'path1, 'paths)}.using(mod))} }
  }

  case class PathLazyModify[T, U](doModify: (T, U => U) => T) { self =>
    /** see [[PathModify.using]] */
    def using(mod: U => U): T => T = obj => doModify(obj, mod)

    /** see [[PathModify.usingIf]] */
    def usingIf(condition: Boolean)(mod: U => U): T => T =
      obj =>
        if (condition) doModify(obj, mod)
        else obj

    /** see [[PathModify.setTo]] */
    def setTo(v: U): T => T = obj => doModify(obj, _ => v)

    /** see [[PathModify.setToIfDefined]] */
    def setToIfDefined(v: Option[U]): T => T = v.fold((obj: T) => obj)(setTo)

    /** see [[PathModify.setToIf]] */
    def setToIf(condition: Boolean)(v: => U): T => T =
      if (condition) setTo(v)
      else obj => obj

    def andThenModify[V](f2: PathLazyModify[U, V]): PathLazyModify[T, V] =
      PathLazyModify[T, V]((t, vv) => self.doModify(t, u => f2.doModify(u, vv)))
  }

  trait QuicklensFunctor[F[_]] {
    def map[A, B](fa: F[A], f: A => B): F[B]
    def each[A](fa: F[A], f: A => A): F[A] = map(fa, f)
    def eachWhere[A](fa: F[A], f: A => A, cond: A => Boolean): F[A] = map(fa, x => if cond(x) then f(x) else x)
  }

  object QuicklensFunctor {
    given QuicklensFunctor[List] with {
      def map[A, B](fa: List[A], f: A => B): List[B] = fa.map(f)
    }

    given QuicklensFunctor[Seq] with {
      def map[A, B](fa: Seq[A], f: A => B): Seq[B] = fa.map(f)
    }

    given QuicklensFunctor[Option] with {
      def map[A, B](fa: Option[A], f: A => B): Option[B] = fa.map(f)
    }

    given [K, M <: ([V] =>> Map[K, V])] : QuicklensFunctor[M] with {
      def map[A, B](fa: M[A], f: A => B): M[B] = fa.view.mapValues(f).toMap.asInstanceOf[M[B]]
    }
  }

  trait QuicklensIndexedFunctor[F[_], I] {
    def at[A](fa: F[A], f: A => A, idx: I): F[A]
    def atOrElse[A](fa: F[A], f: A => A, idx: I, default: => A): F[A]
    def index[A](fa: F[A], f: A => A, idx: I): F[A]
  }

  object QuicklensIndexedFunctor {
    given QuicklensIndexedFunctor[List, Int] with {
      def at[A](fa: List[A], f: A => A, idx: Int): List[A] =
        fa.updated(idx, f(fa(idx)))
      def atOrElse[A](fa: List[A], f: A => A, idx: Int, default: => A): List[A] =
        fa.updated(idx, f(fa.applyOrElse(idx, Function.const(default))))
      def index[A](fa: List[A], f: A => A, idx: Int): List[A] =
        if fa.isDefinedAt(idx) then fa.updated(idx, f(fa(idx))) else fa
    }
    given [K, M <: ([V] =>> Map[K, V])] : QuicklensIndexedFunctor[M, K] with {
      def at[A](fa: M[A], f: A => A, idx: K): M[A] =
        fa.updated(idx, f(fa(idx))).asInstanceOf[M[A]]
      def atOrElse[A](fa: M[A], f: A => A, idx: K, default: => A): M[A] =
        fa.updated(idx, f(fa.applyOrElse(idx, Function.const(default)))).asInstanceOf[M[A]]
      def index[A](fa: M[A], f: A => A, idx: K): M[A] =
        if fa.isDefinedAt(idx) then fa.updated(idx, f(fa(idx))).asInstanceOf[M[A]] else fa
    }
  }

  trait QuicklensEitherFunctor[T[_, _], L, R]:
    def eachLeft(e: T[L, R])(f: L => L): T[L, R]
    def eachRight(e: T[L, R])(f: R => R): T[L, R]

  object QuicklensEitherFunctor:
    given [L, R]: QuicklensEitherFunctor[Either, L, R] with 
      override def eachLeft(e: Either[L, R])(f: (L) => L) = e.left.map(f)
      override def eachRight(e: Either[L, R])(f: (R) => R) = e.map(f)

  extension [F[_]: QuicklensFunctor, A](fa: F[A])
    def each: A = ???
    def eachWhere(cond: A => Boolean): A = ???

  extension [F[_]: ([G[_]] =>> QuicklensIndexedFunctor[G, I]), I, A](fa: F[A])
    def at(idx: I): A = ???
    def atOrElse(idx: I, default: => A): A = ???
    def index(idx: I): A = ???

  extension [T[_, _], L, R](e: T[L, R])(using f: QuicklensEitherFunctor[T, L, R])
    @compileTimeOnly(canOnlyBeUsedInsideModify("eachLeft"))
    def eachLeft: L = sys.error("")

    @compileTimeOnly(canOnlyBeUsedInsideModify("eachRight"))
    def eachRight: R = sys.error("")
  
  private def canOnlyBeUsedInsideModify(method: String) = s"$method can only be used as a path component inside modify"
    
  //
  
  def toPathModify[S: Type, A: Type](obj: Expr[S], f: Expr[(A => A) => S])(using Quotes): Expr[PathModify[S, A]] = '{ PathModify( ${obj}, ${f} ) }

  def fromPathModify[S: Type, A: Type](pathModify: Expr[PathModify[S, A]])(using Quotes): Expr[(A => A) => S] = '{ ${pathModify}.f }

  def to[T: Type, R: Type](f: Expr[T] => Expr[R])(using Quotes): Expr[T => R] = '{ (x: T) => ${ f('x) } }

  def from[T: Type, R: Type](f: Expr[T => R])(using Quotes): Expr[T] => Expr[R] = (x: Expr[T]) => '{ $f($x) }

  def modifyAllImpl[S, A](obj: Expr[S], focus: Expr[S => A], focusesExpr: Expr[Seq[S => A]])(using qctx: Quotes, tpeS: Type[S], tpeA: Type[A]): Expr[PathModify[S, A]] = {
    import qctx.reflect.*

    val focuses = focusesExpr match {
      case Varargs(args) => args
    }

    val modF1 = fromPathModify(modifyImpl(obj, focus))
    val modF = to[(A => A), S] { (mod: Expr[A => A]) =>
      focuses.foldLeft(from[(A => A), S](modF1).apply(mod)) {
        case (objAcc, focus) =>
          val modCur = fromPathModify(modifyImpl(objAcc, focus))
          from[(A => A), S](modCur).apply(mod)
      }
    }

    toPathModify(obj, modF)
  }

  def modifyImpl[S, A](obj: Expr[S], focus: Expr[S => A])(using qctx: Quotes, tpeS: Type[S], tpeA: Type[A]): Expr[PathModify[S, A]] = {
    import qctx.reflect.*
    
    def unsupportedShapeInfo(tree: Tree) = s"Unsupported path element. Path must have shape: _.field1.field2.each.field3.(...), got: $tree"

    enum PathSymbol:
      case Field(name: String)
      case FunctionDelegate(name: String, givn: Term, typeTree: TypeTree, args: List[Term])

    def toPath(tree: Tree): Seq[PathSymbol] = {
      tree match {
        case Select(deep, ident) =>
          toPath(deep) :+ PathSymbol.Field(ident)
        case Apply(Apply(Apply(TypeApply(Ident(s), typeTrees), idents), args), List(ident: Ident)) =>
          idents.flatMap(toPath) :+ PathSymbol.FunctionDelegate(s, ident, typeTrees.last, args)
        case a@Apply(Apply(TypeApply(Ident(s), typeTrees), idents), List(ident: Ident)) =>
          idents.flatMap(toPath) :+ PathSymbol.FunctionDelegate(s, ident, typeTrees.last, List.empty)
        case Apply(deep, idents) =>
          toPath(deep) ++ idents.flatMap(toPath)
        case i: Ident if i.name.startsWith("_") =>
          Seq.empty
        case _ =>
          report.throwError(unsupportedShapeInfo(tree))
      }
    }

    def termMethodByNameUnsafe(term: Term, name: String): Symbol = {
      term.tpe.typeSymbol.memberMethod(name).head
    }

    def termAccessorMethodByNameUnsafe(term: Term, name: String): (Symbol, Int) = {
      val caseFields = term.tpe.typeSymbol.caseFields
      val idx = caseFields.map(_.name).indexOf(name)
      (caseFields.find(_.name == name).get, idx+1)
    }

    def mapToCopy(mod: Expr[A => A], objTerm: Term, path: Seq[PathSymbol]): Term = path match
      case Nil =>
        val apply = termMethodByNameUnsafe(mod.asTerm, "apply")
        Apply(Select(mod.asTerm, apply), List(objTerm))
      case (field: PathSymbol.Field) :: tail =>
        val copy = termMethodByNameUnsafe(objTerm, "copy")
        val (fieldMethod, idx) = termAccessorMethodByNameUnsafe(objTerm, field.name)
        val namedArg = NamedArg(field.name, mapToCopy(mod, Select(objTerm, fieldMethod), tail))
        val fieldsIdxs = 1.to(objTerm.tpe.typeSymbol.caseFields.length)
        val args = fieldsIdxs.map { i =>
          if(i == idx) namedArg
          else Select(objTerm, termMethodByNameUnsafe(objTerm, "copy$default$" + i.toString))
        }.toList
        Apply(
          Select(objTerm, copy),
          args
        )
      case (f: PathSymbol.FunctionDelegate) :: tail =>
        val defdefSymbol = Symbol.newMethod(
          Symbol.spliceOwner,
          "$anonfun",
          MethodType(List("x"))(_ => List(f.typeTree.tpe), _ => f.typeTree.tpe)
        )
        val fMethod = termMethodByNameUnsafe(f.givn, f.name)
        val fun = TypeApply(
          Select(f.givn, fMethod),
          List(f.typeTree)
        )
        val defdefStatements = DefDef(
          defdefSymbol, {
            case List(List(x)) => Some(mapToCopy(mod, x.asExpr.asTerm, tail))
          }
        )
        val closure = Closure(Ref(defdefSymbol), None)
        val block = Block(List(defdefStatements), closure)
        Apply(fun, List(objTerm, block) ++ f.args)
    
    val focusTree: Tree = focus.asTerm
    val path = focusTree match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(p))), _)) =>
        toPath(p)
      case Block(List(DefDef(_, _, _, Some(p))), _) =>
        toPath(p)
      case _ =>
        report.throwError(unsupportedShapeInfo(focusTree))
    }

    val objTree: Tree = obj.asTerm
    val objTerm: Term = objTree match {
      case Inlined(_, _, term) => term
    }
    
    val res: (Expr[A => A] => Expr[S]) = (mod: Expr[A => A]) => mapToCopy(mod, objTerm, path).asExpr.asInstanceOf[Expr[S]]
    toPathModify(obj, to(res))
  }
}