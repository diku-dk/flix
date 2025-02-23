/*
 * Copyright 2019 Magnus Madsen
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

instance LowerBound[Char] {
    pub def minValue(): Char = '\u0000'
}

instance UpperBound[Char] {
    pub def maxValue(): Char = '\uffff'
}

namespace Char {

    ///
    ///  Returns `true` if the given char `c` is an ascii character.
    ///
    @Time(1) @Space(1)
    pub def isAscii(c: Char): Bool = match toInt32(c) {
        case i if i >= 0x0000 and i <= 0x007F => true        // 'NUL'..'DEL'
        case _ => false
    }

    ///
    /// Returns `true` if the given char `c` is a letter character.
    ///
    @Time(1) @Space(1)
    pub def isLetter(c: Char): Bool =
        import static java.lang.Character.isLetter(Char): Bool \ {};
        isLetter(c)

    ///
    /// Returns `true` if the given char `c` is a recognized Unicode digit.
    /// This includes the ASCII range 0..9 but also Arabic-Indic digits, Devagari digits and Fullwidth digits.
    ///
    @Time(1) @Space(1)
    pub def isDigit(c: Char): Bool =
        import static java.lang.Character.isDigit(Char): Bool \ {};
        isDigit(c)

    ///
    /// Returns `true` if the given char `c` is strictly in the range of ASCII digits 0...9.
    ///
    @Time(1) @Space(1)
    pub def isAsciiDigit(c: Char): Bool = match toInt32(c) {
        case i if i >= 0x0030 and i <= 0x0039 => true        // '0'..'9'
        case _ => false
    }

    ///
    /// Returns `true` if the given char `c` is in the range 0...7.
    ///
    @Time(1) @Space(1)
    pub def isOctDigit(c: Char): Bool = match toInt32(c) {
        case i if i >= 0x0030 and i <= 0x0037 => true        // '0'..'7'
        case _ => false
    }

    ///
    /// Returns `true` if the given char `c` is in the range 0...F.
    ///
    @Time(1) @Space(1)
    pub def isHexDigit(c: Char): Bool = match toInt32(c) {
        case i if i >= 0x0030 and i <= 0x0039 => true        // '0'..'9'
        case i if i >= 0x0041 and i <= 0x0046 => true        // 'A'..'F'
        case i if i >= 0x0061 and i <= 0x0066 => true        // 'a'..'f'
        case _ => false
    }

    ///
    /// Returns `true` if the given char `c` is lowercase.
    ///
    @Time(1) @Space(1)
    pub def isLowerCase(c: Char): Bool =
        import static java.lang.Character.isLowerCase(Char): Bool \ {};
        isLowerCase(c)

    ///
    /// Returns `true` if the given char `c` is uppercase.
    ///
    @Time(1) @Space(1)
    pub def isUpperCase(c: Char): Bool =
        import static java.lang.Character.isUpperCase(Char): Bool \ {};
        isUpperCase(c)

    ///
    /// Returns `true` if the given char `c` is a white space character.
    ///
    @Time(1) @Space(1)
    pub def isWhiteSpace(c: Char): Bool =
        import static java.lang.Character.isWhitespace(Char): Bool \ {};
        isWhitespace(c)

    ///
    /// Converts a letter to its lowercase version.
    ///
    /// Returns the original character if it does not have a lowercase version.
    ///
    @Time(1) @Space(1)
    pub def toLowerCase(c: Char): Char =
        import static java.lang.Character.toLowerCase(Char): Char \ {};
        toLowerCase(c)

    ///
    /// Converts a letter to its uppercase version.
    ///
    /// Returns the original character if it does not have a uppercase version.
    ///
    @Time(1) @Space(1)
    pub def toUpperCase(c: Char): Char =
        import static java.lang.Character.toUpperCase(Char): Char \ {};
        toUpperCase(c)

    ///
    /// Returns the character `c` as a string.
    ///
    @Time(1) @Space(1)
    pub def toString(c: Char): String = ToString.toString(c)

    ///
    /// Returns the character `c` as an Int32.
    ///
    @Time(1) @Space(1)
    pub def toInt32(c: Char): Int32 =
        c as Int32

    ///
    /// Returns the respective character for the int `i`.
    ///
    @Time(1) @Space(1)
    pub def fromInt32(i: Int32): Char =
        i as Char

}
