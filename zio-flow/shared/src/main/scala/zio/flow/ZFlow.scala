package zio.flow

//
// ZFlow - models a workflow
//  - terminate, either error or value
//  - create instances that represent running executions of a workflow in progress
//  - instances have persistent state that can be changed in a semi-transactional ways
//  - instance state can be persisted in transparent, introspectable way (e.g. JSON)
//  - business logic
//    - changing in response to external input (events)
//    - initiate activities (interactions with the outside world)
//
// Activity - models an interaction with the outside world
//  - test to see if activity is completed
//  - compensation (undo an activity), "saga pattern"
//  - examples: microservice interaction, REST API call, GraphQL query, database query
//
sealed trait ZFlow[-I, +E, +A] { self =>
  final def *>[I1 <: I, E1 >: E, A1 >: A, B](
    that: ZFlow[I1, E1, B]
  )(implicit A1: Schema[A1], B: Schema[B]): ZFlow[I1, E1, B] =
    (self: ZFlow[I, E, A1]).zip(that).map(_._2)

  final def <*[I1 <: I, E1 >: E, A1 >: A, B](
    that: ZFlow[I1, E1, B]
  )(implicit A1: Schema[A1], B: Schema[B]): ZFlow[I1, E1, A1] =
    (self: ZFlow[I, E, A1]).zip(that).map(_._1)

  final def as[B](b: => Expr[B]): ZFlow[I, E, B] = self.map(_ => b)

  final def catchAll[I1 <: I, E1 >: E, A1 >: A: Schema, E2](f: Expr[E] => ZFlow[I1, E2, A1]): ZFlow[I1, E2, A1] =
    (self: ZFlow[I, E, A1]).foldM(f, ZFlow(_))

  final def flatMap[I1 <: I, E1 >: E, B](f: Expr[A] => ZFlow[I1, E1, B]): ZFlow[I1, E1, B] =
    self.foldM(ZFlow.Halt(_), f)

  final def foldM[I1 <: I, E1 >: E, E2, B](
    error: Expr[E] => ZFlow[I1, E2, B],
    success: Expr[A] => ZFlow[I1, E2, B]
  ): ZFlow[I1, E2, B] = ZFlow.Fold(self, error, success)

  final def map[B](f: Expr[A] => Expr[B]): ZFlow[I, E, B] =
    self.flatMap(a => ZFlow(f(a)))

  final def orElse[I1 <: I, E2, A1 >: A](that: ZFlow[I1, E2, A1])(implicit A1: Schema[A1]): ZFlow[I1, E2, A1] =
    (self: ZFlow[I, E, A1]).catchAll(_ => that)

  final def orElseEither[I1 <: I, E2, A1 >: A, B](
    that: ZFlow[I1, E2, B]
  )(implicit A1: Schema[A1], b: Schema[B]): ZFlow[I1, E2, Either[A1, B]] =
    (self: ZFlow[I, E, A1]).map(Left(_)).catchAll(_ => that.map(Right(_)))

  final def unit: ZFlow[I, E, Unit] = as(())

  final def zip[I1 <: I, E1 >: E, A1 >: A, B](
    that: ZFlow[I1, E1, B]
  )(implicit A1: Schema[A1], B: Schema[B]): ZFlow[I1, E1, (A1, B)] =
    (self: ZFlow[I, E, A1]).flatMap(a => that.map(b => a -> b))
}
object ZFlow                   {
  final case class Return[A](value: Expr[A])                                         extends ZFlow[Any, Nothing, A]
  final case class Halt[E](value: Expr[E])                                           extends ZFlow[Any, E, Nothing]
  final case class Fold[I, E1, E2, A, B](
    value: ZFlow[I, E1, A],
    ke: Expr[E1] => ZFlow[I, E2, B],
    ks: Expr[A] => ZFlow[I, E2, B]
  )                                                                                  extends ZFlow[I, E2, B]
  final case class RunActivity[I, E, A](input: Expr[I], activity: Activity[I, E, A]) extends ZFlow[Any, E, A]
  final case class Transaction[I, E, A](workflow: ZFlow[I, E, A])                    extends ZFlow[I, E, A]
  final case class Input[I](schema: Schema[I])                                       extends ZFlow[I, Nothing, I]
  final case class Define[I, S, E, A](name: String, constructor: Constructor[S], body: S => ZFlow[I, E, A])
      extends ZFlow[I, E, A]

  def apply[A: Schema](a: A): ZFlow[Any, Nothing, A] = Return(Expr(a))

  def apply[A](expr: Expr[A]): ZFlow[Any, Nothing, A] = Return(expr)

  def define[I, S, E, A](name: String, constructor: Constructor[S])(body: S => ZFlow[I, E, A]): ZFlow[I, E, A] =
    Define(name, constructor, body)

  def input[I: Schema]: ZFlow[I, Nothing, I] = Input(implicitly[Schema[I]])

  def transaction[I, E, A](workflow: ZFlow[I, E, A]): ZFlow[I, E, A] =
    Transaction(workflow)
}
