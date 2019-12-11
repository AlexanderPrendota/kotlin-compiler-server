package executors

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException

class JavaRunnerExecutor {
  companion object {
    private val outputStream = ByteArrayOutputStream()
    private val errorOutputStream = ErrorStream(outputStream)
    private val standardOutputStream = OutStream(outputStream)
    @JvmStatic
    fun main(args: Array<String>) {
      val defaultOutputStream = System.out
      try {
        System.setOut(PrintStream(standardOutputStream))
        System.setErr(PrintStream(errorOutputStream))
        val outputObj = RunOutput()
        val className: String
        if (args.isNotEmpty()) {
          className = args[0]
          try {
            val mainMethod = Class.forName(className)
              .getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, args.copyOfRange(1, args.size) as Any)
          }
          catch (e: InvocationTargetException) {
            outputObj.exception = e.cause
          }
          catch (e: NoSuchMethodException) {
            System.err.println("No main method found in project.")
          }
          catch (e: ClassNotFoundException) {
            System.err.println("No main method found in project.")
          }
        }
        else {
          System.err.println("No main method found in project.")
        }
        System.out.flush()
        System.err.flush()
        System.setOut(defaultOutputStream)
        outputObj.text = outputStream.toString()
          .replace("</errStream><errStream>".toRegex(), "")
          .replace("</outStream><outStream>".toRegex(), "")
        print(mapper.writeValueAsString(outputObj))
      }
      catch (e: Throwable) {
        System.setOut(defaultOutputStream)
        println("{\"text\":\"<errStream>" + e.javaClass.name + ": " + e.message)
        e.printStackTrace()
        print("</errStream>\"}")
      }
    }
  }
}

data class RunOutput(var text: String = "", var exception: Throwable? = null)