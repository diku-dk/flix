namespace Test/Exp/Null {

    @test
    def testNull01(): Null = null

    @test
    def testNullUnit01(): Unit = null as Unit

    @test
    def testNullString01(): String = null as String

    @test
    def testNullArray01(): Array[String, Static] = null as Array[String, Static]

    @test
    def testNullChannel01(): Receiver[String] = null as Receiver[String]

    @test
    def testNullRecord01(): {} = null as {}

    @test
    def testNullRecord02(): {name = String} = null as {name = String}

    @test
    def testNullRecord03(): {name = String} = {name = null as String}

    @test
    def testNullRecord04(): {fstName = String, lstName = String} = null as {fstName = String, lstName = String}

    @test
    def testNullRecord05(): {fstName = String, lstName = String} = {fstName = null as String, lstName = null as String}

    @test
    def testNullRef01(): Ref[String, Static] = null as Ref[String, Static]

    @test
    def testNullTuple01(): (String, String) = null as (String, String)

    @test
    def testNullTuple02(): (String, String) = (null as String, "abc")

    @test
    def testNullTuple03(): (String, String) = ("abc", null as String)

    @test
    def testNullOption01(): Option[String] = null as Option[String]

    @test
    def testNullOption02(): Option[String] = Some(null as String)

    @test
    def testNullList01(): List[String] = null as List[String]

    @test
    def testNullList02(): List[String] = (null as String) :: Nil

    @test
    def testNullResult01(): Result[String, String] = null as Result[String, String]

    @test
    def testNullResult03(): Result[String, String] = Ok(null as String)

    @test
    def testNullResult04(): Result[String, String] = Err(null as String)

    @test
    def testNullArrow01(): (String -> String) = (_: String) -> null as String

}
