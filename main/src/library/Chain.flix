/*
 * Copyright 2021 Stephen Tetley
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

///
/// The Chain type.
///
/// A chain is a list represented as an unbalanced binary tree.
/// It supports efficient append and "snoc" - appending elements at the tail
/// of the list.
///
/// Note - the constructors `Empty`, `One` and `Chain` should not be used directly.
///
pub enum Chain[t] {
    case Empty
    case One(t)
    case Chain(Chain[t], Chain[t])
}

instance Eq[Chain[a]] with Eq[a] {
    pub def eq(c1: Chain[a], c2: Chain[a]): Bool = Chain.equals(c1, c2)
}

instance SemiGroup[Chain[a]] {
    pub def combine(c1: Chain[a], c2: Chain[a]): Chain[a] = Chain.append(c1, c2)
}

instance Monoid[Chain[a]] {
    pub def empty(): Chain[a] = Chain.empty()
}

instance Functor[Chain] {
    pub def map(f: a -> b \ ef, c: Chain[a]): Chain[b] \ ef = Chain.map(f, c)
}

instance Applicative[Chain] {
    pub def point(x: a) : Chain[a] = Chain.singleton(x)
    pub def ap(f: Chain[a -> b \ ef], x: Chain[a]) : Chain[b] \ ef = Chain.ap(f, x)
}

instance Monad[Chain] {
    pub def flatMap(f: a -> Chain[b] \ ef, x : Chain[a]) : Chain[b] \ ef = Chain.flatMap(f, x)
}

instance MonadZero[Chain] {
    pub def empty(): Chain[a] = Chain.empty()
}

instance MonadZip[Chain] {
    pub def zipWith(f: (a, b) -> c \ ef, xs: Chain[a], ys: Chain[b]): Chain[c] \ ef = Chain.zipWith(f, xs, ys)
    pub def zipWithA(f: (a, b) -> f[c] \ ef, xs: Chain[a], ys: Chain[b]): f[Chain[c]] \ ef with Applicative[f] = Chain.zipWithA(f, xs, ys)
    pub override def zip(xs: Chain[a], ys: Chain[b]): Chain[(a, b)] = Chain.zip(xs, ys)
    pub override def unzip(xs: Chain[(a, b)]): (Chain[a], Chain[b]) = Chain.unzip(xs)
}

instance Foldable[Chain] {
    pub def foldLeft(f: (b, a) -> b \ ef, s: b, c: Chain[a]): b \ ef = Chain.foldLeft(f, s, c)
    pub def foldRight(f: (a, b) -> b \ ef, s: b, c: Chain[a]): b \ ef = Chain.foldRight(f, s, c)
    pub def foldRightWithCont(f: (a, Unit -> b \ ef) -> b \ ef, s: b, c: Chain[a]): b \ ef = Chain.foldRightWithCont(f, s, c)
    override pub def iterator(r: Region[r], c: Chain[a]): Iterator[a, r] \ Write(r) = Chain.iterator(r, c)
}

instance UnorderedFoldable[Chain] {
    pub def foldMap(f: a -> b \ ef, c: Chain[a]): b \ ef with CommutativeMonoid[b] = Chain.foldMap(f, c)
    override pub def isEmpty(c: Chain[a]): Bool = Chain.isEmpty(c)
    override pub def exists(f: a -> Bool \ ef, c: Chain[a]): Bool \ ef = Chain.exists(f, c)
    override pub def forall(f: a -> Bool \ ef, c: Chain[a]): Bool \ ef = Chain.forall(f, c)
    override pub def memberOf(x: a, c: Chain[a]): Bool with Eq[a] = Chain.memberOf(x, c)
}

instance Traversable[Chain] {
    pub def traverse(f: a -> m[b] \ ef, t: Chain[a]): m[Chain[b]] \ ef with Applicative[m] = Chain.traverse(f, t)
    pub override def sequence(t: Chain[m[a]]): m[Chain[a]] with Applicative[m] = Chain.sequence(t)
}

instance Filterable[Chain] {
    pub def filterMap(f: a -> Option[b] \ ef, x: Chain[a]): Chain[b] \ ef = Chain.filterMap(f, x)
    pub override def filter(f: a -> Bool \ ef, x: Chain[a]): Chain[a] \ ef = Chain.filter(f, x)
}

instance Witherable[Chain]

instance ToString[Chain[a]] with ToString[a] {
    pub def toString(c: Chain[a]): String = region r {
        let sb = new StringBuilder(r);
        StringBuilder.appendString!("Chain#{", sb);
        foreach ((x, i) <- enumerator(r, c)) {
            if (i < 1)
                StringBuilder.appendString!("${x}", sb)
            else
                StringBuilder.appendString!(", ${x}", sb)
        };
        StringBuilder.appendString!("}", sb);
        StringBuilder.toString(sb)
    }
}


namespace Chain {

    ///
    /// A datatype for pattern matching on a chain (traversing left-to-right).
    ///
    pub enum ViewLeft[t] with Eq {
        case NoneLeft
        case SomeLeft(t, Chain[t])
    }

    ///
    /// A datatype for pattern matching on a chain (traversing right-to-left).
    ///
    pub enum ViewRight[t] with Eq {
        case NoneRight
        case SomeRight(Chain[t], t)
    }

    ///
    /// Return the empty chain.
    ///
    pub def empty(): Chain[a] = Empty

    ///
    /// Return the singleton chain with element `x`.
    ///
    pub def singleton(x: a): Chain[a] = One(x)

    ///
    /// Apply every function from `f` to every argument from `x` and return a chain with all results.
    /// For `f = f1, f2, ...` and `x = x1, x2, ...` the results appear in the order
    /// `f1(x1), f1(x2), ..., f2(x1), f2(x2), ...`.
    ///
    pub def ap(f: Chain[a -> b \ ef], x: Chain[a]) : Chain[b] \ ef =
        def loop(g, acc) = match g {
            case NoneLeft        => acc
            case SomeLeft(h, xs) => loop(viewLeft(xs), acc `append` map(h, x))
        };
        loop(viewLeft(f), empty())

    ///
    /// Returns true if and only if `c` is the empty chain.
    ///
    pub def isEmpty(c: Chain[a]): Bool = match c {
        case Empty => true
        case _     => false
    }

    ///
    /// Add element `x` to the left end of chain `c`.
    ///
    pub def cons(x: a, c: Chain[a]): Chain[a] = match c {
        case Empty => One(x)
        case _     => Chain(One(x), c)
    }

    ///
    /// Add element `x` to the right end of chain `c`.
    ///
    pub def snoc(c: Chain[a], x: a): Chain[a] = match c {
        case Empty => One(x)
        case _     => Chain(c, One(x))
    }

    ///
    /// Returns `Some(x)` if `x` is the first element of `c`.
    ///
    /// Returns `None` if `c` is empty.
    ///
    pub def head(c: Chain[a]): Option[a] = match viewLeft(c) {
        case SomeLeft(x, _) => Some(x)
        case _              => None
    }

    ///
    /// Returns `Some(x)` if `x` is the last element of `c`.
    ///
    /// Returns `None` if `c` is empty.
    ///
    pub def last(c: Chain[a]): Option[a] = match viewRight(c) {
        case SomeRight(_, x) => Some(x)
        case _               => None
    }

    ///
    /// Returns the subchain of `c` without the last element.
    /// Returns `None` if the chain `c` is empty.
    ///
    pub def init(c: Chain[a]): Option[Chain[a]] = match viewRight(c) {
        case SomeRight(rs, _) => Some(rs)
        case _                => None
    }

    ///
    /// Returns the length of `c`.
    ///
    pub def length(c: Chain[a]): Int32 = foldRight((_, acc) -> acc + 1, 0, c)

    ///
    /// Returns a new chain formed by appending the chains `c1` and `c2`.
    ///
    pub def append(c1: Chain[a], c2: Chain[a]): Chain[a] = match (c1, c2) {
        case (Empty, c) => c
        case (c, Empty) => c
        case _          => Chain(c1, c2)
    }

    ///
    /// Deconstruct a Chain from left-to-right.
    ///
    /// Returns `ViewLeft(x, rs)` if the chain is non-empty, where `x` is the leftmost
    /// element of the chain `c`, and `rs` is the rest of the chain.
    ///
    /// Returns `NoneLeft` if the chain is empty.
    ///
    pub def viewLeft(c: Chain[a]): ViewLeft[a] =
        def loop(cc, acc, k) = match cc {
            case Empty       => k(NoneLeft)
            case One(x)      => k(SomeLeft(x, acc))
            case Chain(l, r) => loop(l, append(r, acc), k)
        };
        loop(c, Empty, identity)

    ///
    /// Deconstruct a Chain from right-to-left.
    ///
    /// Returns `ViewRight(rs, x)` if the chain is non-empty, where `x` is the rightmost
    /// element of the chain `c``, and `rs` is the front of the chain.
    ///
    /// Returns `NoneRight` if the chain is empty.
    ///
    pub def viewRight(c: Chain[a]): ViewRight[a] =
        def loop(cc, acc, k) = match cc {
            case Empty       => k(NoneRight)
            case One(x)      => k(SomeRight(acc, x))
            case Chain(l, r) => loop(r, append(acc, l),  k)
        };
        loop(c, Empty, identity)

    ///
    /// Returns `true` if and only if `c` contains the element `a`.
    ///
    pub def memberOf(a: a, c: Chain[a]): Bool with Eq[a] = match viewLeft(c) {
        case NoneLeft                 => false
        case SomeLeft(x, _) if x == a => true
        case SomeLeft(_, xs)          => memberOf(a, xs)
    }

    ///
    /// Optionally returns the position of `a` in `c`.
    ///
    pub def indexOf(a: a, c: Chain[a]): Option[Int32] with Eq[a] =
        def loop(v, acc) = match v {
            case NoneLeft        => None
            case SomeLeft(x, xs) => if (x == a) Some(acc) else loop(viewLeft(xs), acc + 1)
        };
        loop(viewLeft(c), 0)

    ///
    /// Alias for `findLeft`.
    ///
    /// The function `f` must be pure.
    ///
    pub def find(f: a -> Bool, c: Chain[a]): Option[a] = findLeft(f, c)

    ///
    /// Optionally returns the first element of `c` that satisfies the predicate `f` when searching from left to right.
    ///
    /// The function `f` must be pure.
    ///
    pub def findLeft(f: a -> Bool, c: Chain[a]): Option[a] = match viewLeft(c) {
        case NoneLeft        => None
        case SomeLeft(x, rs) => if (f(x)) Some(x) else findLeft(f, rs)
    }

    ///
    /// Optionally returns the first element of `c` that satisfies the predicate `f` when searching from right to left.
    ///
    /// The function `f` must be pure.
    ///
    pub def findRight(f: a -> Bool, c: Chain[a]): Option[a] = match viewRight(c) {
        case NoneRight        => None
        case SomeRight(rs, x) => if (f(x)) Some(x) else findRight(f, rs)
    }

    ///
    /// Returns a list of all integers between `b` (inclusive) and `e` (exclusive).
    ///
    /// Returns `Nil` if `b >= e`.
    ///
    pub def range(b: Int32, e: Int32): Chain[Int32] =
        def loop(i, acc) = if (i >= e) acc else loop(i + 1, snoc(acc, i));
        loop(b, Chain.empty())

    ///
    /// Returns a list with the element `a` repeated `n` times.
    ///
    /// Returns `Nil` if `n < 0`.
    ///
    pub def repeat(n: Int32, a: a): Chain[a] =
        def loop(i, acc) = {
            if (i <= 0)
                acc
            else
                loop(i - 1, cons(a, acc))
        };
        loop(n, Empty)

    ///
    /// Alias for `scanLeft`.
    ///
    pub def scan(f: (b, a) -> b \ ef, s: b, c: Chain[a]): Chain[b] \ ef = scanLeft(f, s, c)

    ///
    /// Accumulates the result of applying `f` to `c` going left to right.
    ///
    /// That is, the result is of the form: `s :: f(s, x1) :: f(f(s, x1), x2)  ...`.
    ///
    pub def scanLeft(f: (b, a) -> b \ ef, s: b, c: Chain[a]): Chain[b] \ ef =
        def loop(a, cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(x, xs) =>
                let aa = f(a, x);
                loop(aa, xs, snoc(acc, aa))
        };
        loop(s, c, singleton(s))

    ///
    /// Accumulates the result of applying `f` to `c` going right to left.
    ///
    /// That is, the result is of the form: `... f(xn-1, f(xn, s)) :: f(xn, s) :: s`.
    ///
    pub def scanRight(f: (a, b) -> b \ ef, s: b, c: Chain[a]): Chain[b] \ ef =
        def loop(a, cc, acc) = match viewRight(cc) {
            case NoneRight        => acc
            case SomeRight(xs, x) =>
                let aa = f(x, a);
                loop(aa, xs, cons(aa, acc))
        };
        loop(s, c, singleton(s))

    ///
    /// Returns the result of applying `f` to every element in `c`.
    ///
    /// That is, the result is of the form: `f(x1) :: f(x2) :: ...`.
    ///
    pub def map(f: a -> b \ ef, c: Chain[a]): Chain[b] \ ef =
        def loop(cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(a, rs) => loop(rs, snoc(acc, f(a)))
        };
        loop(c, Empty)

    ///
    /// Returns the result of applying `f` to every element in `c` along with that element's index.
    ///
    /// That is, the result is of the form: `f(x1, 0) :: f(x2, 1) :: ...`.
    ///
    pub def mapWithIndex(f: (Int32, a) -> b \ ef, c: Chain[a]): Chain[b] \ ef =
        def loop(cc, i, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(x, rs) =>
                let a = f(i, x);
                loop(rs, i + 1, snoc(acc, a))
        };
        loop(c, 0, Empty)

    ///
    /// Returns the result of applying `f` to every element in `c` and concatenating the results.
    ///
    pub def flatMap(f: a -> Chain[b] \ ef, c: Chain[a]): Chain[b] \ ef =
        def loop(cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(a, rs) => loop(rs, append(acc, f(a)))
        };
        loop(c, Empty)

    ///
    /// Returns the reverse of `c`.
    ///
    pub def reverse(c: Chain[a]): Chain[a] =
        def loop(cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(a, rs) => loop(rs, cons(a, acc))
        };
        loop(c, Empty)

    ///
    /// Returns `c` with `a` inserted between every two adjacent elements.
    ///
    pub def intersperse(a: a, c: Chain[a]): Chain[a] =
        def loop(cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(x, rs) => loop(rs, acc `snoc` a `snoc` x)
        };
        match viewLeft(c) {
            case NoneLeft        => Empty
            case SomeLeft(x, rs) => loop(rs, singleton(x))
        }

    ///
    /// Returns the number of elements in `c` that satisfy the predicate `f`.
    ///
    pub def count(f: a -> Bool \ ef, c: Chain[a]): Int32 \ ef =
        def loop(cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(a, rs) => if (f(a)) loop(rs, acc + 1) else loop(rs, acc)
        };
        loop(c, 0)

    ///
    /// Returns the sum of all elements in the chain `c`.
    ///
    pub def sum(c: Chain[Int32]): Int32 =
        Foldable.sum(c)

    ///
    /// Returns the sum of all elements in the chain `c` according to the function `f`.
    ///
    pub def sumWith(f: a -> Int32 \ ef, c: Chain[a]): Int32 \ ef =
        Foldable.sumWith(f, c)

    ///
    /// Returns the product of all elements in the list `l`.
    ///
    pub def product(c: Chain[Int32]): Int32 =
        Foldable.product(c)

    ///
    /// Returns the product of all elements in the list `l` according to the function `f`.
    ///
    pub def productWith(f: a -> Int32 \ ef, c: Chain[a]): Int32 \ ef =
        Foldable.productWith(f, c)

    ///
    /// Returns the concatenation of the elements in `c`.
    ///
    pub def flatten(c: Chain[Chain[a]]): Chain[a] =
        foldLeft(append, empty(), c)

    ///
    /// Returns `true` if and only if at least one element in `c` satisfies the predicate `f`.
    ///
    /// Returns `false` if `c` is empty.
    ///
    pub def exists(f: a -> Bool \ ef, c: Chain[a]): Bool \ ef = match viewLeft(c) {
        case NoneLeft               => false
        case SomeLeft(x, _) if f(x) => true
        case SomeLeft(_, xs)        => exists(f, xs)
    }

    ///
    /// Returns `true` if and only if all elements in `c` satisfy the predicate `f`.
    ///
    /// Returns `true` if `c` is empty.
    ///
    pub def forall(f: a -> Bool \ ef, c: Chain[a]): Bool \ ef = match viewLeft(c) {
        case NoneLeft                   => true
        case SomeLeft(x, _) if not f(x) => false
        case SomeLeft(_, xs)            => forall(f, xs)
    }

    ///
    /// Returns a list of every element in `c` that satisfies the predicate `f`.
    ///
    /// The function `f` must be pure.
    ///
    pub def filter(f: a -> Bool \ ef, c: Chain[a]): Chain[a] \ ef =
        def loop(cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(a, rs) => if (f(a)) loop(rs, snoc(acc, a)) else loop(rs, acc)
        };
        loop(c, Empty)

    ///
    /// Applies `f` to a start value `s` and all elements in `c` going from left to right.
    ///
    /// That is, the result is of the form: `f(...f(f(s, x1), x2)..., xn)`.
    ///
    pub def foldLeft(f: (b, a) -> b \ ef, s: b, c: Chain[a]): b \ ef = match viewLeft(c) {
        case NoneLeft        => s
        case SomeLeft(x, xs) => foldLeft(f, f(s, x), xs)
    }

    ///
    /// Applies `f` to a start value `s` and all elements in `c` going from right to left.
    ///
    /// That is, the result is of the form: `f(x1, ...f(xn-1, f(xn, s))...)`.
    ///
    pub def foldRight(f: (a, b) -> b \ ef, s: b, c: Chain[a]): b \ ef = match viewRight(c) {
        case NoneRight        => s
        case SomeRight(xs, x) => foldRight(f, f(x, s), xs)
    }

    ///
    /// Applies `f` to a start value `z` and all elements in `c` going from right to left.
    ///
    /// That is, the result is of the form: `f(x1, ...f(xn-1, f(xn, z))...)`.
    /// A `foldRightWithCont` allows early termination by not calling the continuation.
    ///
    pub def foldRightWithCont(f: (a, Unit -> b \ ef) -> b \ ef, z: b, c: Chain[a]): b \ ef = match viewLeft(c) {
        case NoneLeft        => z
        case SomeLeft(x, xs) => f(x, _ -> foldRightWithCont(f, z, xs))
    }

    ///
    /// Returns the result of mapping each element and combining the results.
    ///
    pub def foldMap(f: a -> b \ ef, c: Chain[a]): b \ ef with Monoid[b] =
        foldLeft((acc, x) -> Monoid.combine(acc, f(x)), Monoid.empty(), c)

    ///
    /// Returns `c` without the first `n` elements.
    ///
    /// Returns `Nil` if `n > length(c)`.
    /// Returns `c` if `n < 0`.
    ///
    pub def dropLeft(n: Int32, c: Chain[a]): Chain[a] =
        if (n <= 0)
            c
        else
            match viewLeft(c) {
                case NoneLeft        => Empty
                case SomeLeft(_, xs) => dropLeft(n - 1, xs)
            }

    ///
    /// Returns `c` without the last `n` elements.
    ///
    /// Returns `Nil` if `n > length(c)`.
    /// Returns `c` if `n < 0`.
    ///
    pub def dropRight(n: Int32, c: Chain[a]): Chain[a] =
        if (n <= 0)
            c
        else
            match viewRight(c) {
                case NoneRight        => Empty
                case SomeRight(xs, _) => dropRight(n - 1, xs)
            }

    ///
    /// Returns `c` without the longest prefix that satisfies the predicate `f`.
    ///
    pub def dropWhileLeft(f: a -> Bool \ ef, c: Chain[a]): Chain[a] \ ef = match viewLeft(c) {
        case SomeLeft(x, xs) if (f(x)) => dropWhileLeft(f, xs)
        case _                         => c
    }

    ///
    /// Returns `c` without the longest suffix that satisfies the predicate `f`.
    ///
    pub def dropWhileRight(f: a -> Bool \ ef, c: Chain[a]): Chain[a] \ ef = match viewRight(c) {
        case SomeRight(xs, x) if (f(x)) => dropWhileRight(f, xs)
        case _                          => c
    }

    ///
    /// Returns the first `n` elements of `c`.
    ///
    /// Returns `c` if `n > length(c)`.
    /// Returns `Nil` if `n < 0`.
    ///
    pub def takeLeft(n: Int32, c: Chain[a]): Chain[a] =
        def loop(i, cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case _ if i < 1      => acc
            case SomeLeft(a, rs) => loop(i - 1, rs, snoc(acc, a))
        };
        if (n < 0)
            Empty
        else
            loop(n, c, Empty)

    ///
    /// Returns the last `n` elements of `c`.
    ///
    /// Returns `c` if `n > length(c)`.
    /// Returns `Nil` if `n < 0`.
    ///
    pub def takeRight(n: Int32, c: Chain[a]): Chain[a] =
        def loop(i, cc, acc) = match viewRight(cc) {
            case NoneRight        => acc
            case _ if i < 1       => acc
            case SomeRight(rs, a) => loop(i - 1, rs, cons(a, acc))
        };
        if (n < 0)
            Empty
        else
            loop(n, c, Empty)

    ///
    /// Returns the longest prefix of `c` that satisfies the predicate `f`.
    ///
    pub def takeWhileLeft(f: a -> Bool \ ef, c: Chain[a]): Chain[a] \ ef =
        def loop(cc, acc) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(a, rs) => if (f(a)) loop(rs, snoc(acc, a)) else acc
        };
        loop(c, Empty)

    ///
    /// Returns the longest suffix of `c` that satisfies the predicate `f`.
    ///
    pub def takeWhileRight(f: a -> Bool \ ef, c: Chain[a]): Chain[a] \ ef =
        def loop(cc, acc) = match viewRight(cc) {
            case SomeRight(rs, a) if (f(a)) => loop(rs, cons(a, acc))
            case _                          => acc
        };
        loop(c, Empty)

    ///
    /// Collects the results of applying the partial function `f` to every element in `c`.
    ///
    pub def filterMap(f: a -> Option[b] \ ef, c: Chain[a]): Chain[b] \ ef =
        let step = (acc, x) -> match f(x) {
            case None    => acc
            case Some(v) => snoc(acc, v)
        };
        foldLeft(step, empty(), c)

    ///
    /// Returns the first non-None result of applying the partial function `f` to each element of `c`.
    ///
    /// Returns `None` if every element of `c` is `None`.
    ///
    pub def findMap(f: a -> Option[b] \ ef, c: Chain[a]): Option[b] \ ef = match viewLeft(c) {
        case NoneLeft        => None
        case SomeLeft(x, xs) => match f(x) {
            case None    => findMap(f, xs)
            case Some(v) => Some(v)
        }
    }

    ///
    /// Returns a chain where the element at index `i` is `(a, b)` where
    /// `a` is the element at index `i` in `c1` and `b` is the element at index `i` in `c2`.
    ///
    /// If either `c1` or `c2` becomes depleted, then no further elements are added to the resulting chain.
    ///
    pub def zip(c1: Chain[a], c2: Chain[b]): Chain[(a,b)] =
        def loop(cc1, cc2, acc) = match (viewLeft(cc1), viewLeft(cc2)) {
            case (SomeLeft(a, rs), SomeLeft(b, qs)) => loop(rs, qs, snoc(acc, (a, b)))
            case _                                  => acc
        };
        loop(c1, c2, empty())

    ///
    /// Returns a chain where the element at index `i` is `f(a, b)` where
    /// `a` is the element at index `i` in `c1` and `b` is the element at index `i` in `c2`.
    ///
    /// If either `c1` or `c2` becomes depleted, then no further elements are added to the resulting chain.
    ///
    pub def zipWith(f: (a, b) -> c \ ef, c1: Chain[a], c2: Chain[b]): Chain[c] \ ef =
        def loop(cc1, cc2, acc) = match (viewLeft(cc1), viewLeft(cc2)) {
            case (SomeLeft(a, rs), SomeLeft(b, qs)) => loop(rs, qs, snoc(acc, f(a, b)))
            case _                                  => acc
        };
        loop(c1, c2, empty())

    ///
    /// Generalize `zipWith` to an applicative functor `f`.
    ///
    pub def zipWithA(f: (a, b) -> m[c] \ ef, xs: Chain[a], ys: Chain[b]): m[Chain[c]] \ ef with Applicative[m] =
        use Functor.{<$>};
        use Applicative.{<*>, point};
        def loop(v1, v2, k) = match (v1, v2) {
            case (SomeLeft(x, c1), SomeLeft(y, c2)) => loop(viewLeft(c1), viewLeft(c2), ks -> k(cons <$> f(x,y) <*> ks))
            case (_, _)                             => k(point(empty()))
        };
        loop(viewLeft(xs), viewLeft(ys), upcast(identity))


    ///
    /// Returns a pair of chains, the first containing all first components in `c`
    /// and the second containing all second components in `c`.
    ///
    pub def unzip(c: Chain[(a, b)]): (Chain[a], Chain[b]) =
        def loop(cc, acc1, acc2) = match viewLeft(cc) {
            case NoneLeft             => (acc1, acc2)
            case SomeLeft((a, b), rs) => loop(rs, snoc(acc1, a), snoc(acc2, b))
        };
        loop(c, Chain.empty(), Chain.empty())

    ///
    /// `mapAccumLeft` is a stateful version of `map`. The accumulating paramter `s` is updated at each
    /// step in a left-to-right traversal.
    ///
    pub def mapAccumLeft(f: (s, a) -> (s, b) \ ef, start: s, c: Chain[a]): (s, Chain[b]) \ ef =
        def loop(s1, cc, k) = match viewLeft(cc) {
            case NoneLeft        => k((s1, empty()))
            case SomeLeft(a, rs) => {
                let (s2, b) = f(s1, a);
                loop(s2, rs, match (s3, ks) -> k((s3, cons(b, ks))))
            }
        };
        loop(start, c, identity)

    ///
    /// `mapAccumRight` is a stateful version of `map`. The accumulating parameter `s` is updated at each
    /// step in a right-to-left traversal.
    ///
    pub def mapAccumRight(f: (s, a) -> (s, b) \ ef, start: s, c: Chain[a]): (s, Chain[b]) \ ef =
        def loop(s1, cc, k) = match viewRight(cc) {
            case NoneRight        => k((s1, empty()))
            case SomeRight(rs, a) => {
                let (s2, b) = f(s1, a);
                loop(s2, rs, match (s3, ks) -> k((s3, snoc(ks, b))))
           }
        };
        loop(start, c, identity)

    ///
    /// Applies `f` to every element of `c`.
    ///
    pub def foreach(f: a -> Unit \ ef, c: Chain[a]): Unit \ ef = match viewLeft(c) {
        case NoneLeft => ()
        case SomeLeft(x, xs) => f(x); foreach(f, xs)
    }

    ///
    /// Returns `c` as a list.
    ///
    pub def toList(c: Chain[a]): List[a] =
        foldRight((x, acc) -> x :: acc, Nil, c)

    ///
    /// Returns `c` as a MutDeque.
    ///
    pub def toMutDeque(r: Region[r], c: Chain[a]): MutDeque[a, r] \ Write(r) =
        let d = new MutDeque(r);
        Chain.foreach(x -> MutDeque.pushBack(x, d), c);
        d

    ///
    /// Returns `c` as a mutable list.
    ///
    pub def toMutList(r: Region[r], c: Chain[a]): MutList[a, r] \ Write(r) = region r2 {
        Array.toMutList(r, toArray(r2, c)) // `Array.toMutList` respects the invariant of `MutList`
    }

    ///
    /// Returns the list `c` as a set.
    ///
    pub def toSet(c: Chain[a]): Set[a] with Order[a] =
        foldRight((x, acc) -> Set.insert(x, acc), Set.empty(), c)

    ///
    /// Returns the chain of pairs `c` that represents an association list as a map.
    ///
    /// If `c` contains multiple mappings with the same key, `toMap` does not
    /// make any guarantees about which mapping will be in the resulting map.
    ///
    pub def toMap(c: Chain[(a, b)]): Map[a, b] with Order[a] =
        foldRight((x, acc) -> Map.insert(fst(x), snd(x), acc), Map.empty(), c)

    ///
    /// Returns the chain `c` as an array.
    ///
    pub def toArray(r: Region[r], c: Chain[a]): Array[a, r] \ Write(r) = match head(c) {
        case None    => [] @ r
        case Some(x) =>
            let arr = [x; length(c)] @ r;
            foreach(match (b, i) -> arr[i] = b, zipWithIndex(c));
            arr
        }

    ///
    /// Returns an iterator over `c`.
    ///
    pub def iterator(r: Region[r], c: Chain[a]): Iterator[a, r] \ Write(r) =
        let cursor = ref viewLeft(c) @ r;
        let done = () -> match (deref cursor) {
            case NoneLeft    => true
            case SomeLeft(_) => false
        };
        let next = () -> match (deref cursor) {
            case NoneLeft        => bug!("Empty Iterator!")
            case SomeLeft(x, xs) => {
                cursor := viewLeft(xs);
                x
            }
        };
        Iterator(done, next)

    ///
    /// Returns an iterator over `c` zipped with the indices of the elements.
    ///
    pub def enumerator(r: Region[r], c: Chain[a]): Iterator[(a, Int32), r] \ Write(r) =
        iterator(r, c) |> Iterator.zipWithIndex

    ///
    /// Returns the chain `c` as a Nel.
    ///
    pub def toNel(c: Chain[a]): Option[Nel[a]] =
        match viewLeft(c) {
            case NoneLeft        => None
            case SomeLeft(x, rs) => Nel(x, Chain.toList(rs)) |> Some
        }

    ///
    /// Returns the chain `c` as a Nec.
    ///
    pub def toNec(c: Chain[a]): Option[Nec[a]] =
        match viewLeft(c) {
            case NoneLeft        => None
            case SomeLeft(x, rs) => foldLeft(Nec.snoc, Nec.singleton(x), rs) |> Some
        }

    ///
    /// Returns `true` if and only if `c1` and `c2` and equal.
    ///
    pub def equals(c1: Chain[a], c2: Chain[a]): Bool with Eq[a] =
        // Note: Chains are considered equal if their (ordered) list of elements are equal.
        // Because they may have different shapes due to construction we use a view to
        // decide equality which imposes an order on the Chain.
        match (viewLeft(c1), viewLeft(c2)) {
            case (NoneLeft, NoneLeft)                           => true
            case (SomeLeft(x, xs), SomeLeft(y, ys)) if (x == y) => equals(xs, ys)
            case _                                              => false
        }

    ///
    /// Sort chain `c` so that elements are ordered from low to high according to their `Order` instance.
    ///
    /// The sort is not stable, i.e., equal elements may appear in a different order than in the input `c`.
    ///
    /// The sort implementation is a Quicksort.
    ///
    pub def sort(c: Chain[a]): Chain[a] with Order[a] = region r {
        toArray(r, c) !> Array.sort! |> Array.toChain
    }

    /// Sort chain `c` so that elements are ordered from low to high according to the `Order` instance
    /// for the values obtained by applying `f` to each element.
    ///
    /// The sort is not stable, i.e., equal elements may appear in a different order than in the input `c`.
    ///
    /// The sort implementation is a Quicksort.
    ///
    pub def sortBy(f: a -> b, c: Chain[a]): Chain[a] with Order[b] = region r {
        toArray(r, c) !> Array.sortBy!(f) |> Array.toChain
    }

    ///
    /// Sort chain `c` so that elements are ordered from low to high according to the comparison function `cmp`.
    ///
    /// The sort is not stable, i.e., equal elements may appear in a different order than in the input `c`.
    ///
    /// The sort implementation is a Quicksort.
    ///
    pub def sortWith(cmp: (a,a) -> Comparison, c: Chain[a]): Chain[a] = region r {
        toArray(r, c) !> Array.sortWith!(cmp) |> Array.toChain
    }

    ///
    /// Helper function for `traverse` and `sequence`.
    ///
    /// Builds an "applicative chain" from an applicative chain of the front of the chain
    /// "snocing" a last element of one applictive action.
    ///
    def snocA(mxs: f[Chain[a]], mx: f[a]): f[Chain[a]] with Applicative[f] =
        (((xs, x) -> snoc(xs, x)) `Functor.map` mxs) `Applicative.ap` mx

    ///
    /// Returns the result of running all the actions in the chain `c`.
    ///
    pub def sequence(c: Chain[m[a]]): m[Chain[a]] with Applicative[m] =
        def loop(v, acc) = match v {
            case NoneLeft        => acc
            case SomeLeft(x, rs) => loop(viewLeft(rs), snocA(acc, x))
        };
        loop(viewLeft(c), Applicative.point(empty()))

    ///
    /// Returns the result of applying the applicative mapping function `f` to all the elements of the
    /// chain `c`.
    ///
    pub def traverse(f: a -> m[b] \ ef, c: Chain[a]): m[Chain[b]] \ ef with Applicative[m] =
        def loop(v, acc) = match v {
            case NoneLeft        => upcast(() -> acc)()
            case SomeLeft(x, rs) => loop(viewLeft(rs), snocA(acc, f(x)))
        };
        loop(viewLeft(c), Applicative.point(empty()))

    ///
    /// Returns the concatenation of the string representation
    /// of each element in `c` with `sep` inserted between each element.
    ///
    pub def join(sep: String, c: Chain[a]): String with ToString[a] =
        Foldable.join(sep, c)

    ///
    /// Returns the concatenation of the string representation
    /// of each element in `c` according to `f` with `sep` inserted between each element.
    ///
    pub def joinWith(f: a -> String \ ef, sep: String, c: Chain[a]): String \ ef =
        Foldable.joinWith(f, sep, c)

    ///
    /// Returns a chain where each element `e` is mapped to `(e, i)` where `i`
    /// is the index of `e`.
    ///
    pub def zipWithIndex(c: Chain[a]): Chain[(a, Int32)] =
        def loop(cc, acc, i) = match viewLeft(cc) {
            case NoneLeft        => acc
            case SomeLeft(a, rs) => loop(rs, snoc(acc, (a, i)), i + 1)
        };
        loop(c, Empty, 0)

    ///
    /// Shuffles `c` using the Fisher–Yates shuffle.
    ///
    pub def shuffle(rnd: Random.Random, c: Chain[a]): Chain[a] \ NonDet = region r {
        toArray(r, c) !> Array.shuffle(rnd) |> Array.toChain
    }

}
