/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.google.devtools.ksp.test

import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.codegen.CodegenTestCase
import org.jetbrains.kotlin.codegen.GenerationUtils
import com.google.devtools.ksp.KotlinSymbolProcessingExtension
import com.google.devtools.ksp.KspOptions
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import com.google.devtools.ksp.processor.AbstractTestProcessor
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.test.ConfigurationKind
import java.io.File
import org.jetbrains.kotlin.test.TestJdkKind

abstract class AbstractKotlinKSPTest : CodegenTestCase() {
    companion object {
        const val TEST_PROCESSOR = "// TEST PROCESSOR:"
        val EXPECTED_RESULTS = "// EXPECTED:"
    }

    override fun doMultiFileTest(wholeFile: File, files: List<TestFile>) {
        val javaFiles = listOfNotNull(writeJavaFiles(files))
        createEnvironmentWithMockJdkAndIdeaAnnotations(ConfigurationKind.NO_KOTLIN_REFLECT, emptyList(), TestJdkKind.FULL_JDK_6, *(javaFiles.toTypedArray()))
        val testProcessorName = wholeFile
            .readLines()
            .filter { it.startsWith(TEST_PROCESSOR) }
            .single()
            .substringAfter(TEST_PROCESSOR)
            .trim()
        val testProcessor = Class.forName("com.google.devtools.ksp.processor.$testProcessorName").newInstance() as AbstractTestProcessor
        val logger = MessageCollectorBasedKSPLogger(PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false))
        val analysisExtension =
            KotlinSymbolProcessingExtension(KspOptions.Builder().apply {
                javaSourceRoots.addAll(javaFiles.map { File(it.parent) }.distinct())
                classOutputDir = File("/tmp/kspTest/classes/main")
                javaOutputDir = File("/tmp/kspTest/src/main/java")
                kotlinOutputDir = File("/tmp/kspTest/src/main/kotlin")
                resourceOutputDir = File("/tmp/kspTest/src/main/resources")
            }.build(), logger, testProcessor)
        val project = myEnvironment.project
        AnalysisHandlerExtension.registerExtension(project, analysisExtension)
        loadMultiFiles(files)
        GenerationUtils.compileFiles(myFiles.psiFiles, myEnvironment, classBuilderFactory)
        val result = testProcessor.toResult()
        val expectedResults = wholeFile
            .readLines()
            .dropWhile { !it.startsWith(EXPECTED_RESULTS) }
            .drop(1)
            .takeWhile { !it.startsWith("// END") }
            .map { it.substring(3).trim() }
        TestCase.assertEquals(expectedResults.joinToString("\n"), result.joinToString("\n"))
    }
}