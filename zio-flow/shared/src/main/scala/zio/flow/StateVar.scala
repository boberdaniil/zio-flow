package zio.flow

trait StateVar[A] { self =>
  def get: Expr[A] = modify(a => (a, a))

  def set(a: Expr[A]): Expr[Unit] =
    modify[Unit](_ => ((), a))

  def modify[B](f: Expr[A] => (Expr[B], Expr[A])): Expr[B] =
    Expr.Modify(self, (e: Expr[A]) => Expr.tuple2(f(e)))

  def updateAndGet(f: Expr[A] => Expr[A]): Expr[A] =
    modify { a =>
      val a2 = f(a)
      (a2, a2)
    }

  def update(f: Expr[A] => Expr[A]): Expr[Unit] = updateAndGet(f).unit
}
