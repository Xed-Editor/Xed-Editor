/*
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 */
package io.github.rosemoe.sora.text;

import androidx.annotation.NonNull;

/**
 * Utility for ASCII function characters
 *
 * @author Rosemoe
 */
public class FunctionCharacters {

    private final static String[] names = {
            "NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK",
            "BEL", "BS", "HT", "LF", "VT", "FF", "CR", "SO",
            "SI", "DLE", "DC1", "DC2", "DC3", "DC4", "NAK",
            "SYN", "ETB", "CAN", "EM", "SUB", "ESC", "FS",
            "GS", "RS", "US", "SP"
    };

    /**
     * Check if the letter is ASCII function character.
     */
    public static boolean isFunctionCharacter(char letter) {
        return letter < 32 || letter == 127;
    }

    /**
     * Check if the letter is ASCII function character, '\t' excluded.
     */
    public static boolean isEditorFunctionChar(char letter) {
        return letter != '\t' && isFunctionCharacter(letter);
    }

    /**
     * Get the name of function character
     */
    @NonNull
    public static String getNameForFunctionCharacter(char letter) {
        if (letter < 32) {
            return names[letter];
        } else if (letter == 127) {
            return "DEL";
        }
        return "UNK";
    }

}
