package org.kframework.parser



import org.kframework.attributes.Source
import org.kframework.parser.concrete2kore.ParseInModule
import org.junit.Test
import org.junit.Assert._
import org.kframework.minikore.{KoreToMini, MiniToKore}
import org.kframework.kore.ADT.SortLookup
//import org.kframework.kore._
//import org.kframework.definition.Module

import org.kframework.minikore.MiniKore._
import org.kframework.minikore.MiniToKore
import org.kframework.minikore.KDefinitionDSL._
import org.kframework.minikore.KOREDefinition._
import org.kframework.parser.KOREDowner._


object ExpDefinition {

  val expString =
    """
      module EXP
        syntax Exp ::= "0" [klabel(0)]
        syntax Exp ::= "1" [klabel(1)]
        syntax Exp ::= "2" [klabel(2)]
        syntax Exp ::= "3" [klabel(3)]
        syntax Exp ::= "4" [klabel(4)]
        syntax Exp ::= "5" [klabel(5)]
        syntax Exp ::= "6" [klabel(6)]
        syntax Exp ::= "7" [klabel(7)]
        syntax Exp ::= "8" [klabel(8)]
        syntax Exp ::= "9" [klabel(9)]

        syntax Exp ::= Exp "+" Exp [klabel(p), plus]
        syntax Exp ::= Exp "-" Exp [minus, klabel(m)]
        syntax Exp ::= Exp "*" Exp [klabel(t), times]
        syntax Exp ::= Exp "/" Exp [klabel(d), div]

        rule 3 + 3 => 6
        rule 9 - 4 => 5
        rule 7 * 0 => 0
      endmodule
    """

  val Exp = Sort("Exp")
  val EXP: Module = module("EXP",
    syntax(Exp) is "0" att klabel("0"),
    syntax(Exp) is "1" att klabel("1"),
    syntax(Exp) is "2" att klabel("2"),
    syntax(Exp) is "3" att klabel("3"),
    syntax(Exp) is "4" att klabel("4"),
    syntax(Exp) is "5" att klabel("5"),
    syntax(Exp) is "6" att klabel("6"),
    syntax(Exp) is "7" att klabel("7"),
    syntax(Exp) is "8" att klabel("8"),
    syntax(Exp) is "9" att klabel("9"),

    syntax(Exp) is (Exp, "+", Exp) att(klabel("p"), "plus"),
    syntax(Exp) is (Exp, "-", Exp) att("minus", klabel("m")),
    syntax(Exp) is (Exp, "*", Exp) att(klabel("t"), "times"),
    syntax(Exp) is (Exp, "/", Exp) att(klabel("d"), "div")

    // priority( >("p", "t") , >("m", "d") ),
    //    rule(term("p", term("3"), term("3")), term("6")),
    //    rule(term("m", term("9"), term("4")), term("5")),
    //    rule(term("t", term("7"), term("0")), term("0"))
  ) att KoreToMini.iMainModule
}


class ParserBootstrapRewritingTest {
  val kParser = new ParseInModule(MiniToKore(KOREDef).mainModule)
  def runParser(parser: ParseInModule, toParse: String, parseAs: String): Pattern =
    parser.parseString(toParse, SortLookup(parseAs), Source(""))._1 match {
      case Right(x) => KoreToMini(x)
      case Left(y) => throw new Error("runParser error: " + y.toString)
    }
  def parseK(toParse: String, parseAs: String): Pattern = runParser(kParser, toParse, parseAs)

  def printInfo(name: String, parsedPattern: Pattern, origModule: Module, downedModule: Module): Unit = {
    println(name ++ " PARSED AS:")
    println("===================")
    println(parsedPattern)
    println("===================")
    println(name ++ " ORIG MODULE:")
    println("=====================")
    println(origModule)
    println("=====================")
    println(name ++ " DOWNED MODULE:")
    println("=======================")
    println(downedModule)
    println("=======================")
    println("\n\n")
  }

//  def rewriteInDefinition(defn: String, topModuleName: String, term: String, termSort: String, maxSteps: Option[Integer]): K = {
//
//    val parsedDefn: K         = preProcess(parseK(defn, KDefinition))
//    val topModule: Module     = downRules(downModules(parsedDefn, Map.empty)(topModuleName))
//    val parser: ParseInModule = new ParseInModule(topModule)
//    val parsedTerm: K         = runParser(parser, term, ADT.SortLookup(termSort))
//
//    // val definition: Definition           = Definition(topModule, Set(topModule))
//    val initRewriter: InitializeRewriter = new InitializeRewriter(new KapiGlobal, HookProvider.get(new KExceptionManager(new GlobalOptions)), new InitializeRewriter.InitializeDefinition)
//    val symbRewriter: Rewriter           = initRewriter(topModule)
//
//    symbRewriter.execute(parsedTerm, Optional.ofNullable(maxSteps.orNull)).k
//  }


  @Test def expressionTest(): Unit = {
    import ExpDefinition._
    val parsed = preProcess(parseK(expString, "KDefinition"))
    val downed = downModules(parsed)
    //printInfo("EXP", parsed, EXP, downed)
    assertEquals(Seq(EXP), downed)
  }

  def kdefFixpoint(): Unit = {

    //val KORE_STRING = io.Source.fromFile("/Users/lpena/kframework/k/kernel/src/test/scala/org/kframework/parser/kore.k").mkString
    val KORE_STRING = io.Source.fromFile("src/test/scala/org/kframework/parser/kore.k").mkString
    val parsed = preProcess(parseK(KORE_STRING, "KDefinition"))
    val downed = downModules(parsed)

    KOREDef.modules.foreach { case module =>
      //printInfo(name, parsed, module, downed(name))
      //assertEquals(module, downRules(downed(name)))
    }
  }








//    public InitializeRewriter(KapiGlobal g,
  //                              Map<String, MethodHandle> hookProvider,
  //                              InitializeDefinition initializeDefinition) {
  //        this(g.fs, g.deterministicFunctions, g.globalOptions, g.kem, g.smtOptions, hookProvider, g.kompileOptions.transition, g.kRunOptions, g.files, initializeDefinition);
  //    }
  //
  //    @Override
  //    public synchronized Rewriter apply(Module module) {
  //        TermContext initializingContext = TermContext.builder(new GlobalContext(fs, deterministicFunctions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.INITIALIZING))
  //                .freshCounter(0).build();
  //        Definition evaluatedDef = initializeDefinition.invoke(module, kem, initializingContext.global());
  //
  //        GlobalContext rewritingContext = new GlobalContext(fs, deterministicFunctions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);
  //        rewritingContext.setDefinition(evaluatedDef);
  //
  //        return new SymbolicRewriterGlue(module, evaluatedDef, transitions, initializingContext.getCounterValue(), rewritingContext, kem);
  //    }

  //  Map<String, MethodHandle> hookProvider = HookProvider.get(kem);
  //  InitializeRewriter.InitializeDefinition initializeDefinition = new InitializeRewriter.InitializeDefinition();
  //  //
  //  //
  //  initializeRewriter = new InitializeRewriter(fs, javaExecutionOptions.deterministicFunctions, kRunOptions.global, kem, kRunOptions.experimental.smt, hookProvider, kompileOptions.transition, kRunOptions, files, initializeDefinition);



}