
namespace X {
    use A.Color
    use A/B.Currency
    use A/B.Kelvin
    use A.{Color => MyColor}
    use A/B.{Currency => MyCurrency}
    use A/B.Meter
    use A/B.{Meter => MyMeter}

    pub def f(): Color = Color.Red
    pub def g(): Color = Color.Red
    pub def h(): Currency = Currency.USD
    pub def i(): Currency = Currency.CAD
    pub def j(): Kelvin = Kelvin.Kelvin(123)
    pub def k(): MyColor = MyColor.Red
    pub def l(): MyCurrency = MyCurrency.USD
    pub def m(): MyCurrency = MyCurrency.CAD
    pub def n(): Meter = 123
    pub def o(): MyMeter = 123
}

namespace Y {
    use B.Kilogram
    use B.Acre

    pub def f(): Kilogram = Kilogram.Kilogram(123)
    pub def g(): Acre = 123
}

namespace A {
    pub enum Color {
        case Red,
        case Blu
    }
    namespace B {
        pub enum Currency {
            case USD,
            case CAD
        }
        pub enum Kelvin(Int32)
        pub type alias Meter = Int32
    }
}

namespace B {
    pub enum Kilogram(Int32)
    pub type alias Acre = Int32
}
