// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - structure interface.
 *
 * Copyright (c) 2010 Madis Janson
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package yeti.lang;

public interface Struct {
    /**
     * Get field by interned name.
     * Warning: the behaviour is undefined when field does not exists!
     */
    Object get(String field);

    /**
     * Get field by index (corresponding to name(field)).
     * Warning: the behaviour is undefined when field does not exists!
     */
    Object get(int field);

    /**
     * Set field by interned name to given value.
     * Warning: the behaviour is undefined when field does not exists!
     */
    void set(String field, Object value);

    /**
     * Field count.
     */
    int count();

    /**
     * Field name by field index (must be sorted alphabetically).
     * Warning: the behaviour is undefined when field does not exists!
     */
    String name(int field);

    /**
     * Field name by field index for equality operations.
     * If the field should not participate in equality comparisions,
     * then eqName should return the empty string literal "" instance
     * (so that the returned value == "" is true).
     */
    String eqName(int field);

    /**
     * Returns reference struct or field value.
     * If the field is immutable, then the field value will be returned
     * and index[at] is assigned -1. Otherwise a reference struct is
     * returned and index[at] is assigned a field index in the returned struct.
     * The index[at + 1] is assigned 1 when the field shouldn't participate
     * in equality comparisions (eqName(fielt) == ""), and 0 otherwise.
     */
    Object ref(int field, int[] index, int at);
}
