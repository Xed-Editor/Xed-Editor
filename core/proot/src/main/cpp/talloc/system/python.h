/*
 * Copyright (c) 2023      Andreas Schneider <asn@samba.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef _SAMBA_PYTHON_H
#define _SAMBA_PYTHON_H

/*
 * With Python 3.6 Cpython started to require C99. With Python 3.12 they
 * started to mix code and variable declarations so disable the warnings.
 */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeclaration-after-statement"
#include <Python.h>
#pragma GCC diagnostic pop

#endif /* _SAMBA_PYTHON_H */
