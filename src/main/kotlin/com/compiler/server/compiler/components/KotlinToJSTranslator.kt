package com.compiler.server.compiler.components

import com.compiler.server.model.ErrorDescriptor
import com.compiler.server.model.TranslationJSResult
import com.compiler.server.model.toExceptionDescriptor
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.facade.K2JSTranslator
import org.jetbrains.kotlin.js.facade.MainCallParameters
import org.jetbrains.kotlin.js.facade.TranslationResult
import org.jetbrains.kotlin.js.facade.exceptions.TranslationException
import org.jetbrains.kotlin.psi.KtFile
import org.springframework.stereotype.Service
import java.util.*

@Service
class KotlinToJSTranslator(
  private val kotlinEnvironment: KotlinEnvironment,
  private val errorAnalyzer: ErrorAnalyzer
) {

  private val JS_CODE_FLUSH = "kotlin.kotlin.io.output.flush();\n"
  private val JS_CODE_BUFFER = "\nkotlin.kotlin.io.output.buffer;\n"

  fun translate(files: List<KtFile>, arguments: List<String>): TranslationJSResult {
    val errors = errorAnalyzer.errorsFrom(files, isJs = true)
    return try {
      if (errorAnalyzer.isOnlyWarnings(errors)) {
        doTranslate(files, arguments).also {
          it.addWarnings(errors)
        }
      }
      else {
        TranslationJSResult(errors = errors)
      }
    }
    catch (e: Exception) {
      TranslationJSResult(exception = e.toExceptionDescriptor())
    }
  }

  @Throws(TranslationException::class)
  private fun doTranslate(
    files: List<KtFile>,
    arguments: List<String>
  ): TranslationJSResult {
    val currentProject = kotlinEnvironment.coreEnvironment.project
    val configuration = JsConfig(currentProject, kotlinEnvironment.jsEnvironment)
    val reporter = object : JsConfig.Reporter() {
      override fun error(message: String) {}
      override fun warning(message: String) {}
    }
    val translator = K2JSTranslator(configuration)
    val result = translator.translate(
      reporter,
      files,
      MainCallParameters.mainWithArguments(arguments)
    )
    return if (result is TranslationResult.Success) {
      TranslationJSResult(JS_CODE_FLUSH + result.getCode() + JS_CODE_BUFFER)
    }
    else {
      val errors = HashMap<String, List<ErrorDescriptor>>()
      for (psiFile in files) {
        errors[psiFile.name] = ArrayList()
      }
      errorAnalyzer.errorsFrom(result.diagnostics.all(), errors)
      TranslationJSResult(errors = errors)
    }
  }
}