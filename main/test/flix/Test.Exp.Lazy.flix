namespace Test/Exp/Lazy {

    @test
    def testUnitLazy01(): Lazy[Unit] =
        lazy ()

    @test
    def testBoolLazy01(): Lazy[Bool] =
        lazy true

    @test
    def testBoolLazy02(): Lazy[Bool] =
        lazy false

    @test
    def testCharLazy01(): Lazy[Char] =
        lazy 'a'

    @test
    def testFloat32Lazy01(): Lazy[Float32] =
        lazy 12.34f32

    @test
    def testFloat64Lazy01(): Lazy[Float64] =
        lazy 12.34f64

    @test
    def testBigDecimalLazy01(): Lazy[BigDecimal] =
        lazy 12.34ff

    @test
    def testInt8Lazy01(): Lazy[Int8] =
        lazy 12i8

    @test
    def testInt16Lazy01(): Lazy[Int16] =
        lazy 12i16

    @test
    def testInt32Lazy01(): Lazy[Int32] =
        lazy 12i32

    @test
    def testInt64Lazy01(): Lazy[Int64] =
        lazy 12i64

    @test
    def testBigIntLazy01(): Lazy[BigInt] =
        lazy 12ii

    @test
    def testStringLazy01(): Lazy[String] =
        lazy "string"

    @test
    def testArrayOfIntLazy01(): Lazy[Array[Int32, Static]] \ IO =
        let array = [1, 2];
        lazy array

    @test
    def testArrayOfStringLazy01(): Lazy[Array[String, Static]] \ IO =
        let array = ["str", "str2"];
        lazy array

    @test
    def testTupleLazy01(): Lazy[(String, String)] =
        lazy ("str", "str2")

    @test
    def testNestedLazy01(): Lazy[Lazy[Int32]] =
        lazy (lazy 123)

    @test
    def testNestedLazy02(): Lazy[Int32 -> Lazy[String]] =
        lazy (x -> if (x < 0) lazy "neg" else lazy "pos")

    @test
    def testNestedLazy03(): Poly[Lazy[Int32], Lazy[String]] =
        Both(lazy 2, lazy "2")

    @test
    def testNestedLazy04(): Poly[Lazy[Int32], Lazy[Int32]] =
        let x = lazy 15;
        Both(x, x)

    @test
    def testNestedLazy05(): Lazy[Poly[Lazy[Int32], String]] =
        lazy (This(lazy 5))

    @test
    def testNestedEnumLazy01(): Lazy[Card] =
        lazy Card(Hearts, N(4))

    @test
    def testPolyLazy01(): Lazy[Poly[Int32, String]] =
        lazy This(123)

    @test
    def testPolyLazy02(): Lazy[Poly[String, String]] =
        lazy That("str")

    @test
    def testPolyLazy03(): Lazy[Poly[String, Int32]] =
        lazy Both("str", 123)

    pub enum Suit {
        case Hearts, Diamonds, Clubs, Spades
    }

    pub enum Rank {
        case N(Int32), Jack, Queen, King, Ace
    }

    pub enum Card {
        case Card(Suit, Rank)
    }

    pub enum Poly[a, b] {
        case None,
        case This(a)
        case That(b)
        case Both(a, b)
    }
}
