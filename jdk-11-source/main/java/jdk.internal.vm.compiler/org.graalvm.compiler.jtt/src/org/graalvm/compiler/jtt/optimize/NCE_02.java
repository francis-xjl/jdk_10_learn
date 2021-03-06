/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */


package org.graalvm.compiler.jtt.optimize;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Test case for null check elimination.
 */
public class NCE_02 extends JTTTest {

    public static class TestClass {
        int field1;
        int field2 = 23;
    }

    public static TestClass object = new TestClass();

    public static int test() {
        TestClass o = object;
        o.field1 = 11;
        // expect non-null
        o.field1 = 22;
        // expect non-null
        return o.field2;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
