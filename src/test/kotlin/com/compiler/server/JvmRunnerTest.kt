package com.compiler.server

import com.compiler.server.base.BaseExecutorTest
import com.compiler.server.model.JvmExecutionResult
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmRunnerTest : BaseExecutorTest() {

  @Test
  fun `base execute test JVM`() {
    val executionResult = run(
      code = "fun main() {\n println(\"Hello, world!!!\")\n}",
      contains = "Hello, world!!!",
      addByteCode = false,
    )
    assertNull((executionResult as JvmExecutionResult).jvmByteCode, "Bytecode should not be generated")
  }

  @Test
  fun `jvm bytecode`() {
    val executionResult = run(
      code = "fun main() {\n println(\"Hello, world!!!\")\n}",
      contains = "Hello, world!!!",
      addByteCode = true,
    )
    
    val byteCode = (executionResult as JvmExecutionResult).jvmByteCode!!
    assertContains(byteCode, "public static synthetic main([Ljava/lang/String;)V", message = byteCode)
    assertContains(byteCode, "public final static main()V", message = byteCode)
    assertContains(byteCode, "LDC \"Hello, world!!!\"", message = byteCode)
    assertContains(byteCode, "INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/Object;)V", message = byteCode)
  }

  @Test
  fun `execute test JVM different package`() {
    run(
      code = "package com.example\nfun main() {\n println(\"Hello, world!!!\")\n}",
      contains = "Hello, world!!!"
    )
  }

  @Test
  fun `no main class jvm test`() {
    runWithException(
      code = "fun main1() {\n    println(\"sdf\")\n}",
      contains = "IllegalArgumentException",
      message = "No main method found in project",
    )
  }

  @Test
  fun `multiple main classes jvm test`() {
    runWithException(
      code = """
                fun main() {
                    println("sdf")
                }
                class A {
                    companion object {
                        @JvmStatic
                        fun main(x: Array<String>) {
                        }
                    }
                }""".trimIndent(),
      contains = "IllegalArgumentException",
      message = "Multiple classes in project contain main methods found: A, FileKt",
    )
  }

  @Test
  fun `base execute test JVM multi`() {
    run(
      code = listOf(
        "import cat.Cat\n\nfun main(args: Array<String>) {\nval cat = Cat(\"Kitty\")\nprintln(cat.name)\n}",
        "package cat\n    class Cat(val name: String)"
      ),
      contains = "Kitty"
    )
  }

  @Test
  fun `correct kotlin version test jvm`() {
    run(
      code = "fun main() {\n    println(KotlinVersion?.CURRENT)\n}",
      contains = version().substringBefore("-")
    )
  }

  @Test
  fun `report deprecation warning`() {
    val result = run(
      contains = "<outStream>97\n</outStream>", /*language=kotlin */ code = """
        fun main() {
          println("abc"[0].toInt())
        }
      """.trimIndent()
    )

    assertEquals(1, result.compilerDiagnostics.size)
    assertEquals(1, result.compilerDiagnostics[0].interval?.start?.line)
    assertEquals(19, result.compilerDiagnostics[0].interval?.start?.ch)
    assertEquals(
      "'fun toInt(): Int' is deprecated. Conversion of Char to Number is deprecated. Use Char.code property instead.",
      result.compilerDiagnostics[0].message
    )
  }

  @Test
  fun `warnings after exception`() {
    // language=kotlin
    val result = run(
      """
        fun main() {
          println("")g
          var someVar = 1
        }
      """.trimIndent(), ""
    )

    assertContains(result.compilerDiagnostics.map { it.message }, "Variable is unused.")
  }

  @Test
  fun `warnings without exception`() {
    // language=kotlin
    val result = run(
      """
        fun main() {
          println("")
          var someVar = 1
        }
      """.trimIndent(), "")

    assertContains(result.compilerDiagnostics.map { it.message }, "Variable is unused.")
  }

  @Test
  fun `basic arithmetic operations`() {
    run(
      code = """
        fun main() {
          val a = 10
          val b = 5
          println("Addition: ${"$"}{a + b}")
          println("Subtraction: ${"$"}{a - b}")
          println("Multiplication: ${"$"}{a * b}")
          println("Division: ${"$"}{a / b}")
        }
      """.trimIndent(),
      contains = """
        Addition: 15
        Subtraction: 5
        Multiplication: 50
        Division: 2
      """.trimIndent()
    )
  }

  @Test
  fun `collections operations`() {
    run(
      code = """
        fun main() {
          val numbers = mutableListOf(1, 2, 3, 4, 5)
          numbers.add(6)
          numbers.removeAt(0)
          val filtered = numbers.filter { it % 2 == 0 }
          val mapped = filtered.map { it * 2 }
          println(mapped)
        }
      """.trimIndent(),
      contains = "[4, 8, 12]"
    )
  }

  @Test
  fun `class instantiation and method calls`() {
    run(
      code = """
        class Greeter(val name: String) {
          fun greet(): String {
            return "Hello, ${"$"}name!"
          }
        }

        fun main() {
          val greeter = Greeter("Kotlin")
          println(greeter.greet())
        }
      """.trimIndent(),
      contains = "Hello, Kotlin!"
    )
  }

  @Test
  fun `simple coroutine execution`() {
    run(
      code = """
        import kotlinx.coroutines.*

        fun main() = runBlocking {
          var result = ""
          val job = launch {
            delay(100)
            result = "Coroutine executed"
          }
          job.join()
          println(result)
        }
      """.trimIndent(),
      contains = "Coroutine executed"
    )
  }

  @Test
  fun `try catch block for runtime exception`() {
    run(
      code = """
        fun main() {
          var exceptionCaught = false
          try {
            val result = 10 / 0
            println(result) // This line should not be reached
          } catch (e: ArithmeticException) {
            exceptionCaught = true
            println("Caught ArithmeticException as expected")
          } catch (e: Exception) {
            println("Caught unexpected exception: ${"$"}{e.message}")
          }
          println("Exception caught: ${"$"}{exceptionCaught}")
        }
      """.trimIndent(),
      contains = """
        Caught ArithmeticException as expected
        Exception caught: true
      """.trimIndent()
    )
  }
}
