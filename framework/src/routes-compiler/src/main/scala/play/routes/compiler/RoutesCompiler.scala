/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package play.routes.compiler

import java.io.File
import org.apache.commons.io.FileUtils
import scala.io.Codec

/**
 * provides a compiler for routes
 */
object RoutesCompiler {

  private val LineMarker = "\\s*// @LINE:\\s*(\\d+)\\s*".r

  /**
   * A source file that's been generated by the routes compiler
   */
  trait GeneratedSource {

    /**
     * The original source file associated with this generated source file, if known
     */
    def source: Option[File]

    /**
     * Map the generated line to the original source file line, if known
     */
    def mapLine(generatedLine: Int): Option[Int]
  }

  object GeneratedSource {

    def unapply(file: File): Option[GeneratedSource] = {

      val lines: Array[String] = if (file.exists) {
        FileUtils.readFileToString(file, implicitly[Codec].name).split('\n')
      } else {
        Array.empty[String]
      }

      if (lines.contains("// @GENERATOR:play-routes-compiler")) {
        Some(new GeneratedSource {
          val source: Option[File] =
            lines.find(_.startsWith("// @SOURCE:")).map(m => new File(m.trim.drop(11)))

          def mapLine(generatedLine: Int): Option[Int] = {
            lines.view.take(generatedLine).reverse.collectFirst {
              case LineMarker(line) => Integer.parseInt(line)
            }
          }
        })
      } else {
        None
      }
    }

  }

  /**
   * Compile the given routes file
   *
   * @param file The routes file to compile
   * @param generatedDir The directory to place the generated source code in
   * @param additionalImports Additional imports to add to the output files
   * @param generateReverseRouter Whether the reverse router should be generated
   * @param namespaceReverseRouter Whether the reverse router should be namespaced
   * @return Either the list of files that were generated (right) or the routes compilation errors (left)
   */
  def compile(file: File, generator: RoutesGenerator, generatedDir: File, additionalImports: Seq[String], generateReverseRouter: Boolean = true,
    namespaceReverseRouter: Boolean = false): Either[Seq[RoutesCompilationError], Seq[File]] = {

    val namespace = Option(file.getName).filter(_.endsWith(".routes")).map(_.dropRight(".routes".size))

    val routeFile = file.getAbsoluteFile

    RoutesFileParser.parse(routeFile).right.map { rules =>
      val generated = generator.generate(routeFile, namespace, rules, additionalImports, generateReverseRouter,
        namespaceReverseRouter)
      generated.map {
        case (filename, content) =>
          val file = new File(generatedDir, filename)
          FileUtils.writeStringToFile(file, content, implicitly[Codec].name)
          file
      }
    }
  }
}

