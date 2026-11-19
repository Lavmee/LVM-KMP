package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackageReaderTest {

    private fun kotlin(source: String): String? = PackageReader(source.trimIndent(), nestedComments = true).read()

    private fun java(source: String): String? = PackageReader(source.trimIndent(), nestedComments = false).read()

    @Test
    fun `reads the package of a Kotlin file with or without a semicolon`() {
        assertEquals("com.example.feature", kotlin("package com.example.feature\n\nimport kotlin.math.max\n\nclass A"))
        assertEquals("com.example", kotlin("package com.example;\nclass A"))
    }

    @Test
    fun `reads a name across lines only where a dot continues it, as Kotlin does`() {
        assertEquals("com.example", kotlin("package com.example\nimport kotlin.math.max"))
        assertEquals("com.example.feature", kotlin("package com.example // the module\n    .feature\n\nclass A"))
    }

    @Test
    fun `skips comments before the package, and nested ones in Kotlin`() {
        val source = """
            /*
             * Copyright. package not.this
             * /* nested */ still a comment: package nor.this
             */
            // package neither.this
            package com.example
        """
        assertEquals("com.example", kotlin(source))
    }

    @Test
    fun `ends a Java block comment at its first closing delimiter`() {
        assertEquals("com.example", java("/* /* not nested in Java */\npackage com.example;"))
    }

    @Test
    fun `skips file annotations, their arguments and the grouped form`() {
        val source = """
            @file:JvmName("Names(\")")
            @file:Suppress("unused", 'p'.toString())
            @file:[JvmMultifileClass OptIn(kotlin.ExperimentalStdlibApi::class)]
            @file:kotlin.jvm.JvmSynthetic
            package com.example.names
        """
        assertEquals("com.example.names", kotlin(source))
    }

    @Test
    fun `skips the annotations of a package-info file`() {
        assertEquals("com.example", java("@Deprecated\n@SuppressWarnings({\"a\", \"b\"})\npackage com.example;"))
    }

    @Test
    fun `skips a byte order mark and a shebang line`() {
        assertEquals("com.example", kotlin("﻿#!/usr/bin/env kotlin\npackage com.example"))
    }

    @Test
    fun `drops backticks around segments`() {
        assertEquals("com.example.in.fun", kotlin("package com.example.`in`.`fun`"))
    }

    @Test
    fun `finds no package when code or imports come first`() {
        assertNull(kotlin("import kotlin.math.max\n\nclass A"))
        assertNull(kotlin("class A\npackage com.example"))
        assertNull(kotlin(""))
        assertNull(kotlin("// package com.example"))
        assertNull(kotlin("val packages = 1"))
    }
}
