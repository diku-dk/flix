/*
 * Copyright 2016 Liam Palmer
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

instance LowerBound[Int64] {
    pub def minValue(): Int64 = Int64.minValue()
}

instance UpperBound[Int64] {
    pub def maxValue(): Int64 = Int64.maxValue()
}

namespace Int64 {

    ///
    /// Returns the number of bits used to represent an `Int64`.
    ///
    @Time(1) @Space(1)
    pub def size(): Int32 = 64

    ///
    /// Returns the minimum number representable by an `Int64`.
    ///
    @Time(1) @Space(1)
    pub def minValue(): Int64 = 1i64 <<< (size() - 1)

    ///
    /// Returns the maximum number representable by an `Int64`.
    ///
    @Time(1) @Space(1)
    pub def maxValue(): Int64 = ~~~minValue()

    ///
    /// Returns the smaller of `x` and `y`.
    ///
    @Time(1) @Space(1)
    pub def min(x: Int64, y: Int64): Int64 = if (x <= y) x else y

    ///
    /// Returns the larger of `x` and `y`.
    ///
    @Time(1) @Space(1)
    pub def max(x: Int64, y: Int64): Int64 = if (x >= y) x else y

    ///
    /// Returns the absolute value of `x`.
    /// If the absolute value exceeds maxValue(), -1 is returned.
    ///
    @Time(1) @Space(1)
    pub def abs(x: Int64): Int64 = {
        if      (x >= 0i64)         x
        else if (x == minValue())   -1i64
        else                        -x
    }

    ///
    /// Returns the distance between `x` and `y`.
    /// If this distance exceeds maxValue(), -1 is returned.
    ///
    @Time(1) @Space(1)
    pub def dist(x: Int64, y: Int64): Int64 = {
        if (x >= 0i64 and y >= 0i64)                     abs(x - y)
        else if (x < 0i64 and y < 0i64)                  abs(x - y)
        else if (x == minValue() or y == minValue())    -1i64
        else if (minValue() + abs(x) + abs(y) >= 0i64)  -1i64
        else                                            abs(x - y)
    }

    ///
    /// Returns 1 if x > y, -1 if x < y, and 0 if x = y.
    /// The sign of x - y.
    ///
    @Time(1) @Space(1)
    pub def compare(x: Int64, y: Int64): Int32 = {
        if (x == y)     0
        else if (x < y) -1
        else            1
    }

    ///
    /// Returns 1 if x > 0, -1 if x < 0, and 0 if x = 0.
    /// The sign of x.
    ///
    @Time(1) @Space(1)
    pub def signum(x: Int64): Int32 = compare(x, 0i64)

    ///
    /// Returns the logical right shift of `x` by `distance`.
    /// Only the rightmost 6 bits of `distance` are considered (ie. `distance rem 64`).
    /// A zero is shifted into the leftmost position regardless of sign extension.
    ///
    @Time(1) @Space(1)
    pub def logicalRightShift(dist: {dist = Int32}, x: Int64): Int64 =
        if (x < 0i64 and dist.dist rem size() != 0)
            ((x >>> 1) &&& maxValue()) >>> (dist.dist - 1)
        else
            x >>> dist.dist

    ///
    /// Returns the number of one-bits in the two's complement binary
    /// representation of `x`.
    ///
    @Time(1) @Space(1)
    pub def bitCount(x: Int64): Int32 = {
        if      (x == 0i64)          0
        else if (x rem 2i64 != 0i64) bitCount(logicalRightShift(dist = 1, x)) + 1
        else                         bitCount(logicalRightShift(dist = 1, x))
    }

    ///
    /// Returns the the value obtained by rotating the two's complement
    /// binary representation of `x` right by `distance` bits.
    ///
    pub def rotateRight(dist: {dist = Int32}, x: Int64): Int64 =
        (logicalRightShift(dist = dist.dist, x)) ||| (x <<< -dist.dist)

    ///
    /// Returns the the value obtained by rotating the two's complement
    /// binary representation of `x` left by `distance` bits.
    ///
    @Time(1) @Space(1)
    pub def rotateLeft(dist: {dist = Int32}, x: Int64): Int64 =
        (logicalRightShift(dist = -dist.dist, x)) ||| (x <<< dist.dist)

    ///
    /// Returns the value obtained by reversing the bits in the
    /// two's complement binary representation of `x`.
    ///
    @Time(1) @Space(1)
    pub def reverse(x: Int64): Int64 = reverseHelper(x, 0, size()-1)

    ///
    /// Helper function for `reverse`.
    ///
    def reverseHelper(x: Int64, l: Int32, r: Int32): Int64 =
        if (l >= r) x else reverseHelper(swap(x, l, r), l+1, r-1)

    ///
    /// Helper function for `reverse`.
    ///
    def swap(x: Int64, l: Int32, r: Int32): Int64 = match (getBit(pos = l, x), getBit(pos = r, x)) {
        case (1, 0) => clearBit(pos = l, setBit(pos = r, x))
        case (0, 1) => clearBit(pos = r, setBit(pos = l, x))
        case _ => x
    }

    ///
    /// Returns the position of the highest-order/leftmost one-bit in `x`.
    /// Possible return values: 0 (rightmost bit) - 63 (leftmost bit)
    ///                         -1 if x = 0
    ///
    @Time(1) @Space(1)
    pub def highestOneBitPosition(x: Int64): Int32 =
        // Start at bit 63 and scan right
        oneBitPositionHelper(x, size() - 1, -1)

    ///
    /// Returns the position of the lowest-order/rightmost one-bit in `x`.
    /// Possible return values: 0 (rightmost bit) - 63 (leftmost bit)
    ///                         -1 if x = 0
    ///
    @Time(1) @Space(1)
    pub def lowestOneBitPosition(x: Int64): Int32 =
        // Start at bit 0 and scan left
        oneBitPositionHelper(x, 0, 1)

    ///
    /// Helper function for highestOneBitPosition and lowestOneBitPosition.
    /// If `x` has a one-bit at `position`, return that position.
    /// Otherwise recursively check the next bit in the same way.
    ///
    def oneBitPositionHelper(x: Int64, position: Int32, delta: Int32): Int32 = {
        if (position < 0)                        -1
        else if (position > (size() - 1))        -1
        else if (getBit(pos = position, x) == 1) position
        else if (delta == 0)                     -1
        else                                     oneBitPositionHelper(x, position + delta, delta)
    }

    ///
    /// Returns a value with at most a single one-bit, in the position
    /// of the highest-order/leftmost one-bit in `x`.
    /// Returns 0 if x=0.
    ///
    @Time(1) @Space(1)
    pub def highestOneBit(x: Int64): Int64 =
        bitPositionToInt64(highestOneBitPosition(x))

    ///
    /// Returns a value with at most a single one-bit, in the position
    /// of the highest-order/leftmost one-bit in `x`.
    /// Returns 0 if x=0.
    ///
    @Time(1) @Space(1)
    pub def lowestOneBit(x: Int64): Int64 =
        bitPositionToInt64(lowestOneBitPosition(x))

    ///
    /// Helper function for highestOneBit and lowestOneBit.
    /// Returns a value with a single one-bit at bit number `position`.
    /// Returns 0 if `position` is outside the range 0-63 inclusive.
    ///
    def bitPositionToInt64(position: Int32): Int64 =
        if (position < 0 or position > size() - 1) 0i64 else 1i64 <<< position

    ///
    /// Returns the number of zero bits preceding the
    /// highest-order/leftmost one-bit in `x`.
    /// Returns 64 if x=0.
    ///
    @Time(1) @Space(1)
    pub def numberOfLeadingZeros(x: Int64): Int32 =
        if (x == 0i64) size() else size() - 1 - highestOneBitPosition(x)

    ///
    /// Returns the number of zero bits following the
    /// lowest-order/rightmost one-bit in `x`.
    /// Returns 64 if x=0.
    ///
    @Time(1) @Space(1)
    pub def numberOfTrailingZeros(x: Int64): Int32 =
        if (x == 0i64) size() else lowestOneBitPosition(x)

    ///
    /// Returns the bit of `x` at `position` (either 0 or 1).
    /// Considers the 6 rightmost bits of `position` (`position` mod 64).
    /// The bits of x have positions: 0 (rightmost bit) - 63 (leftmost bit).
    ///
    @Time(1) @Space(1)
    pub def getBit(pos: {pos = Int32}, x: Int64): Int32 =
        if ((x >>> pos.pos) rem 2i64 == 0i64) 0 else 1

    ///
    /// Returns `x` with the bit at position `position` set (to 1).
    /// Considers the 6 rightmost bits of `position` (`position` mod 64).
    /// The bits of x have positions: 0 (rightmost bit) - 63 (leftmost bit)
    ///
    @Time(1) @Space(1)
    pub def setBit(pos: {pos = Int32}, x: Int64): Int64 = x ||| (1i64 <<< pos.pos)

    ///
    /// Returns `x` with the bit at position `position` cleared (to 0).
    /// Considers the 6 rightmost bits of `position` (`position` mod 64).
    /// The bits of x have positions: 0 (rightmost bit) - 63 (leftmost bit)
    ///
    @Time(1) @Space(1)
    pub def clearBit(pos: {pos = Int32}, x: Int64): Int64 = x &&& ~~~(1i64 <<< pos.pos)

    ///
    /// Returns `x` with the bit at position `position` flipped.
    /// Considers the 6 rightmost bits of `position` (`position` mod 64).
    /// The bits of x have positions: 0 (rightmost bit) - 63 (leftmost bit)
    ///
    @Time(1) @Space(1)
    pub def flipBit(pos: {pos = Int32}, x: Int64): Int64 = x ^^^ (1i64 <<< pos.pos)

    ///
    /// Returns the integer binary logarithm of `x`.
    /// If the given value is 0 or negative, 0 is returned.
    ///
    @Time(1) @Space(1)
    pub def log2(x: Int64): Int64 =
        if (x <= 0i64) {
            0i64
        } else {
            highestOneBitPosition(x) |> Int32.toInt64
        }

    ///
    /// Returns the factorial of `x`.
    /// If the given value is negative, 0 is returned.
    ///
    @Time(factorial(x)) @Space(1)
    pub def factorial(x: Int64): Int64 =
        if (x < 0i64) {
            0i64
        } else {
            def loop(y, acc) = match y {
                case 0i64 => acc
                case _    => loop(y - 1i64, y * acc)
            };
            loop(x, 1i64)
        }

    ///
    /// Return a string representation of `x`.
    ///
    @Time(1) @Space(1)
    pub def toString(x: Int64): String = ToString.toString(x)

    ///
    /// Parse the string `s` as an Int64, leading or trailing whitespace is trimmed.
    /// A successful parse is wrapped with `Some(x)`, a parse failure is indicated by `None`.
    ///
    @Time(1) @Space(1)
    pub def fromString(s: String): Option[Int64] = try {
        import java.lang.String.strip(): String \ {};
        import static java.lang.Long.parseLong(String): Int64 \ {};
        Some(s |> strip |> parseLong)
    } catch {
        case _: ##java.lang.NumberFormatException => None
    }

    ///
    /// Parse the string `s` as an Int64, where the `radix` is used while parsing.
    /// Leading or trailing whitespace is trimmed.
    /// A successful parse is wrapped with `Ok(x)`, a parse failure is indicated by `Err(_)`.
    ///
    pub def parse(radix: Int32, s: String): Result[Int64, String] = try {
        import java.lang.String.strip(): String \ {};
        import static java.lang.Long.parseLong(String, Int32): Int64 \ {};
        Ok(parseLong(strip(s), radix))
    } catch {
        case _: ##java.lang.NumberFormatException => Err("Int64.parse")
    }

    ///
    /// Convert `x` to an `Option[Int8]`.
    ///
    /// Returns `Some(x as Int8)` if the numeric value of `x` can be represented exactly.
    ///
    /// Returns `None` if the numeric value of `x` is outside the range of Int8
    /// (i.e. -128 to 127).
    ///
    @Time(1) @Space(1)
    pub def tryToInt8(x: Int64): Option[Int8] =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.byteValue(): Int8 \ {};
        if (x < Int8.toInt64(Int8.minValue()) or x > Int8.toInt64(Int8.maxValue()))
            None
        else
            Some(valueOf(x) |> byteValue)

    ///
    /// Convert `x` to an `Option[Int16]`.
    ///
    /// Returns `Some(x as Int16)` if the numeric value of `x` can be represented exactly.
    ///
    /// Returns `None` if the numeric value of `x` is outside the range of Int16
    /// (i.e. -32768 to 32767).
    ///
    @Time(1) @Space(1)
    pub def tryToInt16(x: Int64): Option[Int16] =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.shortValue(): Int16 \ {};
        if (x < Int16.toInt64(Int16.minValue()) or x > Int16.toInt64(Int16.maxValue()))
            None
        else
            Some(valueOf(x) |> shortValue)

    ///
    /// Convert `x` to an `Option[Int32]`.
    ///
    /// Returns `Some(x as Int32)` if the numeric value of `x` can be represented exactly.
    ///
    /// Returns `None` if the numeric value of `x` is outside the range of Int32
    /// (i.e. -2147483648 to 2147483647).
    ///
    @Time(1) @Space(1)
    pub def tryToInt32(x: Int64): Option[Int32] =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.intValue(): Int32 \ {};
        if (x < Int32.toInt64(Int32.minValue()) or x > Int32.toInt64(Int32.maxValue()))
            None
        else
            Some(valueOf(x) |> intValue)
    ///
    /// Convert `x` to a BigInt.
    ///
    /// The numeric value of `x` is preserved exactly.
    ///
    @Time(1) @Space(1)
    pub def toBigInt(x: Int64): BigInt =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {} as i64ValueOf;
        import java.lang.Long.longValue(): Int64 \ {};
        import static java.math.BigInteger.valueOf(Int64): BigInt \ {} as asBigInt;
        (i64ValueOf(x) |> longValue |> asBigInt)

    ///
    /// Convert `x` to a Float32.
    ///
    /// Warning: The numeric value of `x` may lose precision.
    ///
    @Time(1) @Space(1)
    pub def toFloat32(x: Int64): Float32 =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.floatValue(): Float32 \ {};
        (valueOf(x) |> floatValue)

    ///
    /// Convert `x` to a Float32.
    ///
    /// Warning: The numeric value of `x` may lose precision.
    ///
    @Time(1) @Space(1)
    pub def toFloat64(x: Int64): Float64 =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.doubleValue(): Float64 \ {};
        (valueOf(x) |> doubleValue)

    ///
    /// Convert `x` to a Float32.
    ///
    /// Warning: The numeric value of `x` may lose precision.
    ///
    @Time(1) @Space(1)
    pub def toBigDecimal(x: Int64): BigDecimal =
        import new java.math.BigDecimal(Int64): BigDecimal \ {} as fromInt64;
        fromInt64(x)

    ///
    /// Helper function for the `clamp` conversion functions.
    ///
    def clamp(min: {min = Int64}, max: {max = Int64}, x: Int64): Int64 =
        if (x < min.min)
             min.min
        else
            if (x > max.max)
                max.max
            else
                x

    ///
    /// Convert `x` to an `Int8`.
    ///
    /// Returns `x` clamped within the Int8 range `min` to `max`.
    ///
    @Time(1) @Space(1)
    pub def clampToInt8(min: {min = Int8}, max: {max = Int8}, x: Int64): Int8 =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.byteValue(): Int8 \ {};
        let mini64 = Int8.toInt64(min.min);
        let maxi64 = Int8.toInt64(max.max);
        (valueOf(clamp(min = mini64, max = maxi64, x)) |> byteValue)

    ///
    /// Convert `x` to an `Int16`.
    ///
    /// Returns `x` clamped within the Int16 range `min` to `max`.
    ///
    @Time(1) @Space(1)
    pub def clampToInt16(min: {min = Int16}, max: {max = Int16}, x: Int64): Int16 =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.shortValue(): Int16 \ {};
        let mini64 = Int16.toInt64(min.min);
        let maxi64 = Int16.toInt64(max.max);
        (valueOf(clamp(min = mini64, max = maxi64, x)) |> shortValue)


    ///
    /// Convert `x` to an `Int32`.
    ///
    /// Returns `x` clamped within the Int32 range `min` to `max`.
    ///
    @Time(1) @Space(1)
    pub def clampToInt32(min: {min = Int32}, max: {max = Int32}, x: Int64): Int32 =
        import static java.lang.Long.valueOf(Int64): ##java.lang.Long \ {};
        import java.lang.Long.intValue(): Int32 \ {};
        let mini64 = Int32.toInt64(min.min);
        let maxi64 = Int32.toInt64(max.max);
        (valueOf(clamp(min = mini64, max = maxi64, x)) |> intValue)

}
