namespace Test/Exp/ReifyType {

    @test
    def reifyUnit01(): Bool = reifyType Unit == ReifiedUnit

    @test
    def reifyUnit02(): Bool = reflect() == ReifiedUnit

    @test
    def reifyBool01(): Bool = reifyType Bool == ReifiedBool

    @test
    def reifyBool02(): Bool = reflect(true) == ReifiedBool

    @test
    def reifyChar01(): Bool = reifyType Char == ReifiedChar

    @test
    def reifyChar02(): Bool = reflect('a') == ReifiedChar

    @test
    def reifyFloat3201(): Bool = reifyType Float32 == ReifiedFloat32

    @test
    def reifyFloat3202(): Bool = reflect(1.0f32) == ReifiedFloat32

    @test
    def reifyFloat6401(): Bool = reifyType Float64 == ReifiedFloat64

    @test
    def reifyFloat6402(): Bool = reflect(1.0f64) == ReifiedFloat64

    @test
    def reifyInt801(): Bool = reifyType Int8 == ReifiedInt8

    @test
    def reifyInt802(): Bool = reflect(1i8) == ReifiedInt8

    @test
    def reifyInt1601(): Bool = reifyType Int16 == ReifiedInt16

    @test
    def reifyInt1602(): Bool = reflect(1i16) == ReifiedInt16

    @test
    def reifyInt3201(): Bool = reifyType Int32 == ReifiedInt32

    @test
    def reifyInt3202(): Bool = reflect(1) == ReifiedInt32

    @test
    def reifyInt6401(): Bool = reifyType Int64 == ReifiedInt64

    @test
    def reifyInt6402(): Bool = reflect(1i64) == ReifiedInt64

    @test
    def reifyBigInt01(): Bool = reifyType BigInt == ReifiedBigInt

    @test
    def reifyBigInt02(): Bool = reflect(1ii) == ReifiedBigInt

    @test
    def reifyString01(): Bool = reifyType String == ReifiedString

    @test
    def reifyString02(): Bool = reflect("hello") == ReifiedString

    @test
    def reifyArrayUnit3201(): Bool = reifyType Array[Unit, Static] == ReifiedArray(ReifiedUnit)

    @test
    def reifyArrayUnit3202(): Bool \ IO = reflect([()]) == ReifiedArray(ReifiedUnit)

    @test
    def reifyArrayBool3201(): Bool = reifyType Array[Bool, Static] == ReifiedArray(ReifiedBool)

    @test
    def reifyArrayBool3202(): Bool \ IO = reflect([false]) == ReifiedArray(ReifiedBool)

    @test
    def reifyArrayChar3201(): Bool = reifyType Array[Char, Static] == ReifiedArray(ReifiedChar)

    @test
    def reifyArrayChar3202(): Bool \ IO = reflect(['b']) == ReifiedArray(ReifiedChar)

    @test
    def reifyArrayFloat3201(): Bool = reifyType Array[Float32, Static] == ReifiedArray(ReifiedFloat32)

    @test
    def reifyArrayFloat3202(): Bool \ IO = reflect([1.0f32]) == ReifiedArray(ReifiedFloat32)

    @test
    def reifyArrayFloat6401(): Bool = reifyType Array[Float64, Static] == ReifiedArray(ReifiedFloat64)

    @test
    def reifyArrayFloat6402(): Bool \ IO = reflect([1.0f64]) == ReifiedArray(ReifiedFloat64)

    @test
    def reifyArrayInt801(): Bool = reifyType Array[Int8, Static] == ReifiedArray(ReifiedInt8)

    @test
    def reifyArrayInt802(): Bool \ IO = reflect([1i8]) == ReifiedArray(ReifiedInt8)

    @test
    def reifyArrayInt1601(): Bool = reifyType Array[Int16, Static] == ReifiedArray(ReifiedInt16)

    @test
    def reifyArrayInt1602(): Bool \ IO = reflect([1i16]) == ReifiedArray(ReifiedInt16)

    @test
    def reifyArrayInt3201(): Bool = reifyType Array[Int32, Static] == ReifiedArray(ReifiedInt32)

    @test
    def reifyArrayInt3202(): Bool \ IO = reflect([1]) == ReifiedArray(ReifiedInt32)

    @test
    def reifyArrayInt6401(): Bool = reifyType Array[Int64, Static] == ReifiedArray(ReifiedInt64)

    @test
    def reifyArrayInt6402(): Bool \ IO = reflect([1i64]) == ReifiedArray(ReifiedInt64)

    @test
    def reifyArrayBigInt01(): Bool = reifyType Array[BigInt, Static] == ReifiedArray(ReifiedBigInt)

    @test
    def reifyArrayBigInt02(): Bool \ IO = reflect([1ii]) == ReifiedArray(ReifiedBigInt)

    @test
    def reifyArrayString01(): Bool = reifyType Array[String, Static] == ReifiedArray(ReifiedString)

    @test
    def reifyArrayString02(): Bool \ IO = reflect(["hello"]) == ReifiedArray(ReifiedString)

    def reflect(_: a): ReifiedType = reifyType a

}
