///
/// Flix supports writing code in an imperative
/// style if it is preferable to you.
///
/// One of the features related to this is a
/// foreach-loop.
///
/// In this example we explore how this loop
/// works in Flix.
///
def main(): Unit \ IO =

    ///
    /// Construct a simple list.
    ///
    let l = 1 :: 2 :: 3 :: Nil;

    ///
    /// Here is a simple loop where we print the
    /// elements of `l`.
    ///
    /// A loop starts with the `foreach` keyword
    /// and has a pattern on the left side of
    /// the arrow.
    /// On the right side of the arrow we get
    /// an iterator over `l`.
    ///
    foreach (x <- List.iterator(Static, l))
        println(x);

    ///
    /// Note that we can match the pattern that
    /// the iterator on the right returns.
    ///
    /// In this case, the enumerator is an iterator
    /// which also contains the index of the element.
    ///
    /// This may be familiar to you if you have
    /// some experience with Rust.
    ///
    foreach ((x, i) <- List.enumerator(Static, l))
        println("${(x, i)}");

    ///
    /// foreach-loops are in fact just syntactic
    /// sugar for `foreach` functions.
    ///
    /// Thus, by the type of `foreach` the last
    /// expression in the foreach loop must be
    /// of type unit.
    ///
    let z = ref Nil;
    foreach (x <- List.iterator(Static, l))
        z := x :: deref z;

    ///
    /// The loop above is actually equivalent to
    /// the function call below.
    ///
    /// However, foreach-loops can be much easier
    /// to read than `foreach` function calls.
    ///
    let q = ref Nil;
    List.iterator(Static, l) |>
    Iterator.foreach(match x -> q := x :: deref q);

    ///
    /// This is true in particular for nested
    /// foreach-loops.
    ///
    let k = 4 :: 5 :: 6 :: Nil;
    let w = ref Nil;
    foreach (a <- List.iterator(Static, l)) {
        w := a :: deref w;
        foreach (b <- List.iterator(Static, k))
            w := b :: deref w
    };

    ///
    /// The expression above is equivalent
    /// to the functional style expression
    /// below.
    ///
    let v = ref Nil;
    List.iterator(Static, l) |>
    Iterator.foreach(match a -> {
        v := a :: deref v;
        List.iterator(Static, k) |>
        Iterator.foreach(match b -> v := b :: deref v)
    });

    ///
    /// We can even simplify nested loops
    /// further.
    ///
    /// We can have multiple loops separated by
    /// a semicolon in a single parenthesis.
    ///
    /// This may be familiar to you if you have
    /// some experience with Scala's for-loops.
    ///
    let e = ref Nil;
    foreach (a <- List.iterator(Static, l);
             b <- List.iterator(Static, k))
                e := (a, b) :: deref e;

    ///
    /// The equivalent functional style expression
    /// has a bit more visual noise.
    ///
    let e1 = ref Nil;
    List.iterator(Static, l) |>
    Iterator.foreach(match a -> {
        List.iterator(Static, k) |>
        Iterator.foreach(match b -> e1 := (a, b) :: deref e1)
    });

    ///
    /// Finally, we can also include guards
    /// in our loop.
    ///
    /// With guards, you only gain access
    /// to the values that satisfy the boolean
    /// condition(s).
    ///
    /// These expressions are also separated by a
    /// semicolon.
    ///
    /// Again, this is similar to Scala's
    /// for-loops.
    ///
    let c = ref Nil;
    foreach (a <- List.iterator(Static, l);
             if a > 1;
             b <- List.iterator(Static, k);
             if b < 6)
                c := (a, b) :: deref c;

    ///
    /// Alternatively, we can put a single guard
    /// at the end of the parenthesis.
    ///
    let d = ref Nil;
    foreach (a <- List.iterator(Static, l);
             b <- List.iterator(Static, k);
             if a > 1 and b < 6)
                d := (a, b) :: deref d;

    ///
    /// Here's the equivalent functional expression
    /// which can be a bit cumbersome to read.
    ///
    /// **Summary*
    ///
    /// foreach-loops are great for working with
    /// references and other imperative features,
    /// where functional-style syntax may cumbersome
    /// to read.
    /// They are equivalent to function calls so
    /// there is no difference in performance.
    /// You can nest them, either explicitly or in
    /// the same parenthesis, match on the exact pattern
    /// and include guards.
    ///
    let cf = ref Nil;
    List.iterator(Static, l) |>
    Iterator.foreach(match a -> {
        List.iterator(Static, k) |>
        Iterator.foreach(match b -> {
            if (a > 1 and b < 6) cf := (a, b) :: deref cf else ()
        })
    });

    println("z == q? ${deref z == deref q}");
    println("w == l with k prepended to every element of l");
    println("w == ${deref w}? ${deref w == 6 :: 5 :: 4 :: 3 :: 6 :: 5 :: 4 :: 2 :: 6 :: 5 :: 4 :: 1 :: Nil}");
    println("w == v? ${deref w == deref v}");
    println("e contains all combinations of pairs from l and k");
    println("e (sorted) == ${List.sortBy(match (a, _) -> a, deref e)}");
    println("e == e1? ${deref e == deref e1}");
    println("c contains all combinations of pairs from l and k excluding 1 and 6");
    println("c (sorted) == ${List.sortBy(match (a, _) -> a, deref c)}");
    println("c == d? ${deref c == deref d}");
    println("c == cf? ${deref c == deref cf}")
