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
package io.github.rosemoe.sora.lang.diagnostic;

/**
 * Class for describing a diagnostic region.
 *
 * @author Rosemoe
 */
public final class DiagnosticRegion implements Comparable<DiagnosticRegion> {

    public final static short SEVERITY_NONE = 0;
    public final static short SEVERITY_TYPO = 1;
    public final static short SEVERITY_WARNING = 2;
    public final static short SEVERITY_ERROR = 3;

    /**
     * Id specified by diagnostic provider
     */
    public long id;
    /*
     * The detail of the problem
     **/
    public DiagnosticDetail detail;
    /**
     * The start index of the diagnostic
     */
    public int startIndex;
    /**
     * The end index of the diagnostic
     */
    public int endIndex;
    /**
     * One diagnostic has only one severity specification
     *
     * @see #SEVERITY_NONE
     * @see #SEVERITY_TYPO
     * @see #SEVERITY_WARNING
     * @see #SEVERITY_ERROR
     */
    public short severity;

    public DiagnosticRegion(int startIndex, int endIndex, short severity) {
        this(startIndex, endIndex, severity, 0, null);
    }

    public DiagnosticRegion(int startIndex, int endIndex, short severity, long id) {
        this(startIndex, endIndex, severity, id, null);
    }

    public DiagnosticRegion(int startIndex, int endIndex, short severity, long id, DiagnosticDetail detail) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.severity = severity;
        this.detail = detail;
        this.id = id;
    }

    @Override
    public int compareTo(DiagnosticRegion o) {
        var cmp = Integer.compare(startIndex, o.startIndex);
        if (cmp == 0) {
            cmp = Integer.compare(endIndex, o.endIndex);
        }
        if (cmp == 0) {
            cmp = Short.compare(severity, o.severity);
        }
        if (cmp == 0) {
            cmp = Long.compare(id, o.id);
        }
        return cmp;
    }
}
