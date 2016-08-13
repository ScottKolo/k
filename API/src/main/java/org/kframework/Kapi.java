// Copyright (c) 2016 K Team. All Rights Reserved.
package org.kframework;

import org.kframework.RewriterResult;
import org.kframework.attributes.Att;
import org.kframework.attributes.Source;
import org.kframework.backend.java.compile.KOREtoBackendKIL;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.kore.compile.ExpandMacros;
import org.kframework.backend.java.symbolic.ConjunctiveFormula;
import org.kframework.backend.java.symbolic.InitializeRewriter;
import org.kframework.backend.java.symbolic.JavaBackend;
import org.kframework.backend.java.symbolic.JavaExecutionOptions;
import org.kframework.backend.java.symbolic.ProofExecutionMode;
import org.kframework.backend.java.symbolic.Stage;
import org.kframework.backend.java.symbolic.SymbolicRewriter;
import org.kframework.compile.NormalizeKSeq;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.Kompile;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.krun.KRun;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.krun.ioserver.filesystem.portable.PortableFileSystem;
import org.kframework.main.GlobalOptions;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.rewriter.Rewriter;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.options.SMTOptions;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kframework.Collections.*;

/**
 * KRunAPI
 */
public class Kapi {

    public KapiGlobal kapiGlobal;

    public Kapi(KapiGlobal kapiGlobal) {
        this.kapiGlobal = kapiGlobal;
    }

    public Kapi() {
        this(new KapiGlobal());
    }

    public CompiledDefinition kompile(String def, String mainModuleName) {
        // parse
        Definition parsedDef = DefinitionParser.from(def, mainModuleName);

        // compile (translation pipeline)
        Function<Definition, Definition> pipeline = new JavaBackend(kapiGlobal).steps();
        CompiledDefinition compiledDef = new Kompile(kapiGlobal).run(parsedDef, pipeline);

        return compiledDef;
    }

    public RewriterResult krun(CompiledDefinition compiledDef, String programText, Integer depth) {

        // TODO:DROP
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        // parse program
        BiFunction<String, Source, K> programParser = compiledDef.getProgramParser(kapiGlobal.kem);
        K pgm = programParser.apply(programText, Source.apply("generated by api"));
        K program = KRun.getInitConfig(pgm, compiledDef, kapiGlobal.kem);

        /* TODO: figure out if it is needed
        program = new KTokenVariablesToTrueVariables()
                .apply(compiledDef.kompiledDefinition.getModule(compiledDef.mainSyntaxModuleName()).get(), program);
         */

        // rewrite up to the given depth
        Map<String, MethodHandle> hookProvider = HookProvider.get(kapiGlobal.kem);
        InitializeRewriter.InitializeDefinition initializeDefinition = new InitializeRewriter.InitializeDefinition();
        //
        Rewriter rewriter = (InitializeRewriter.SymbolicRewriterGlue)
            new InitializeRewriter(kapiGlobal,
                javaExecutionOptions,
                hookProvider,
                initializeDefinition)
            .apply(compiledDef.executionModule());
        //
        RewriterResult result = ((InitializeRewriter.SymbolicRewriterGlue) rewriter).execute(program, Optional.ofNullable(depth));
        return result;
    }

    public static void kprint(CompiledDefinition compiledDef, RewriterResult result) {
        // tier-1 dependencies
        GlobalOptions globalOptions = new GlobalOptions();
        KompileOptions kompileOptions = new KompileOptions();
        KRunOptions krunOptions = new KRunOptions();
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        // tier-2 dependencies
        KExceptionManager kem = new KExceptionManager(globalOptions);
        FileUtil files = FileUtil.get(globalOptions, System.getenv());

        // print output
        // from org.kframework.krun.KRun.run()
        KRun.prettyPrint(compiledDef, krunOptions.output, s -> KRun.outputFile(s, krunOptions, files), result.k());
    }

    public static void main(String[] args) {
        if (args.length < 10) {
            System.out.println("usage: <def> <main-module> <pgm>");
            return;
        }
        String def0 = FileUtil.load(new File(args[0])); // "require \"domains.k\" module A syntax KItem ::= \"run\" endmodule"
        String mod0 = args[1]; // "A"

        String def1 = FileUtil.load(new File(args[2])); // "require \"domains.k\" module A syntax KItem ::= \"run\" rule run => ... endmodule"
        String mod1 = args[3]; // "A"
        String pgm1 = FileUtil.load(new File(args[4])); // "run"

        String def2 = FileUtil.load(new File(args[5])); // "require \"domains.k\" module A syntax KItem ::= \"run\" rule run => ... endmodule"
        String mod2 = args[6]; // "A"
        String pgm2 = FileUtil.load(new File(args[7])); // "run"

        String prove = args[8];
        String prelude = args[9];

        Kapi kapi = new Kapi();

        // kompile
        CompiledDefinition compiledDef0 = kapi.kompile(def0, mod0);
        CompiledDefinition compiledDef1 = kapi.kompile(def1, mod1);
        CompiledDefinition compiledDef2 = kapi.kompile(def2, mod2);

        // krun
        RewriterResult result1 = kapi.krun(compiledDef1, pgm1, null);
        kprint(compiledDef1, result1);
        RewriterResult result2 = kapi.krun(compiledDef2, pgm2, null);
        kprint(compiledDef2, result2);

        // kprove
        kprove(compiledDef0, compiledDef1, prove, prelude);

        // kequiv
        kequiv(compiledDef0, compiledDef1, compiledDef2, prove, prelude);

        return;
    }

    /**
     * compiledDef0: for parsing spec rules
     * compiledDef1: for symbolic execution
     * compiledDef2: for symbolic execution
     */
    public static void kequiv(CompiledDefinition compiledDef0, CompiledDefinition compiledDef1, CompiledDefinition compiledDef2, String proofFile, String prelude) {

        GlobalOptions globalOptions = new GlobalOptions();
        KompileOptions kompileOptions = new KompileOptions();
        KRunOptions krunOptions = new KRunOptions();
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        KExceptionManager kem = new KExceptionManager(globalOptions);
        Stopwatch sw = new Stopwatch(globalOptions);
        FileUtil files = FileUtil.get(globalOptions, System.getenv());

        FileSystem fs = new PortableFileSystem(kem, files);
        Map<String, MethodHandle> hookProvider = HookProvider.get(kem); // new HashMap<>();
        InitializeRewriter.InitializeDefinition initializeDefinition = new InitializeRewriter.InitializeDefinition();

        //// setting options

        krunOptions.experimental.prove = proofFile;
        krunOptions.experimental.smt.smtPrelude = prelude;

        SMTOptions smtOptions = krunOptions.experimental.smt;

        //// parse spec file

        Kompile kompile = new Kompile(kompileOptions, globalOptions, files, kem, sw, false);
        Module specModule = kompile.parseModule(compiledDef0, files.resolveWorkingDirectory(proofFile).getAbsoluteFile());

        scala.collection.Set<Module> alsoIncluded = Stream.of("K-TERM", "K-REFLECTION", RuleGrammarGenerator.ID_PROGRAM_PARSING)
                .map(mod -> compiledDef0.getParsedDefinition().getModule(mod).get())
                .collect(org.kframework.Collections.toSet());

        specModule = new JavaBackend(kem, files, globalOptions, kompileOptions)
                .stepsForProverRules()
                .apply(Definition.apply(specModule, org.kframework.Collections.add(specModule, alsoIncluded), Att.apply()))
                .getModule(specModule.name()).get();

        ExpandMacros macroExpander = new ExpandMacros(compiledDef0.executionModule(), kem, files, globalOptions, kompileOptions);

        List<Rule> specRules = stream(specModule.localRules())
                .filter(r -> r.toString().contains("spec.k"))
                .map(r -> (Rule) macroExpander.expand(r))
                .map(r -> ProofExecutionMode.transformFunction(JavaBackend::ADTKVariableToSortedVariable, r))
                .map(r -> ProofExecutionMode.transformFunction(JavaBackend::convertKSeqToKApply, r))
                .map(r -> ProofExecutionMode.transform(NormalizeKSeq.self(), r))
                        //.map(r -> kompile.compileRule(compiledDefinition, r))
                .collect(Collectors.toList());

        //// creating rewritingContext

        GlobalContext initializingContextGlobal = new GlobalContext(fs, javaExecutionOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.INITIALIZING);
        TermContext initializingContext = TermContext.builder(initializingContextGlobal).freshCounter(0).build();
        org.kframework.backend.java.kil.Definition evaluatedDef0 = initializeDefinition.invoke(compiledDef0.executionModule(), kem, initializingContext.global());
        org.kframework.backend.java.kil.Definition evaluatedDef1 = initializeDefinition.invoke(compiledDef1.executionModule(), kem, initializingContext.global());
        org.kframework.backend.java.kil.Definition evaluatedDef2 = initializeDefinition.invoke(compiledDef2.executionModule(), kem, initializingContext.global());

        GlobalContext rewritingContextGlobal1 = new GlobalContext(fs, javaExecutionOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);
        GlobalContext rewritingContextGlobal2 = new GlobalContext(fs, javaExecutionOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);
        rewritingContextGlobal1.setDefinition(evaluatedDef1);
        rewritingContextGlobal2.setDefinition(evaluatedDef2);
        TermContext rewritingContext1 = TermContext.builder(rewritingContextGlobal1).freshCounter(initializingContext.getCounterValue()).build();
        TermContext rewritingContext2 = TermContext.builder(rewritingContextGlobal2).freshCounter(initializingContext.getCounterValue()).build();

        //// massage spec rules

        KOREtoBackendKIL converter1 = new KOREtoBackendKIL(compiledDef0.executionModule(), evaluatedDef0, rewritingContext1.global(), false);
        KOREtoBackendKIL converter2 = new KOREtoBackendKIL(compiledDef0.executionModule(), evaluatedDef0, rewritingContext2.global(), false);

        List<org.kframework.backend.java.kil.Rule> specRules1 = specRules.stream()
                .map(r -> converter1.convert(Optional.<Module>empty(), r))
                .map(r -> new org.kframework.backend.java.kil.Rule(
                        r.label(),
                        r.leftHandSide().evaluate(rewritingContext1), // TODO: drop?
                        r.rightHandSide().evaluate(rewritingContext1), // TODO: drop?
                        r.requires(),
                        r.ensures(),
                        r.freshConstants(),
                        r.freshVariables(),
                        r.lookups(),
                        r.isCompiledForFastRewriting(),
                        r.lhsOfReadCell(),
                        r.rhsOfWriteCell(),
                        r.cellsToCopy(),
                        r.matchingInstructions(),
                        r,
                        rewritingContext1.global())) // register definition to be used for execution of the current rule
                .collect(Collectors.toList());

        List<org.kframework.backend.java.kil.Rule> specRules2 = specRules.stream()
                .map(r -> converter2.convert(Optional.<Module>empty(), r))
                .map(r -> new org.kframework.backend.java.kil.Rule(
                        r.label(),
                        r.leftHandSide().evaluate(rewritingContext2), // TODO: drop?
                        r.rightHandSide().evaluate(rewritingContext2), // TODO: drop?
                        r.requires(),
                        r.ensures(),
                        r.freshConstants(),
                        r.freshVariables(),
                        r.lookups(),
                        r.isCompiledForFastRewriting(),
                        r.lhsOfReadCell(),
                        r.rhsOfWriteCell(),
                        r.cellsToCopy(),
                        r.matchingInstructions(),
                        r,
                        rewritingContext2.global())) // register definition to be used for execution of the current rule
                .collect(Collectors.toList());

        // rename all variables again to avoid any potential conflicts with the rules in the semantics
        int counter = Variable.getCounter();
        specRules1 = specRules1.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());
        Variable.setCounter(counter); // TODO: HACK:
        specRules2 = specRules2.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());

        // rename all variables again to avoid any potential conflicts with the rules in the semantics
        counter = Variable.getCounter();
        List<org.kframework.backend.java.kil.Rule> targetSpecRules1 = specRules1.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());
        Variable.setCounter(counter); // TODO: HACK:
        List<org.kframework.backend.java.kil.Rule> targetSpecRules2 = specRules2.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());

        //// prove spec rules

        SymbolicRewriter rewriter1 = new SymbolicRewriter(rewritingContextGlobal1, kompileOptions, new KRunState.Counter(), converter1);
        SymbolicRewriter rewriter2 = new SymbolicRewriter(rewritingContextGlobal2, kompileOptions, new KRunState.Counter(), converter2);

        assert (specRules1.size() == specRules2.size());
        assert (specRules1.size() == targetSpecRules1.size());
        assert (targetSpecRules1.size() == targetSpecRules2.size());

        List<ConstrainedTerm> startSyncNodes1 = new ArrayList<>();
        List<ConstrainedTerm> startSyncNodes2 = new ArrayList<>();
        List<ConstrainedTerm> targetSyncNodes1 = new ArrayList<>();
        List<ConstrainedTerm> targetSyncNodes2 = new ArrayList<>();
        List<ConjunctiveFormula> ensures = new ArrayList<>();
        List<Boolean> trusted = new ArrayList<>();

        for (int i = 0; i < specRules1.size(); i++) {
            org.kframework.backend.java.kil.Rule startRule1 = specRules1.get(i);
            org.kframework.backend.java.kil.Rule startRule2 = specRules2.get(i);
            org.kframework.backend.java.kil.Rule targetRule1 = targetSpecRules1.get(i);
            org.kframework.backend.java.kil.Rule targetRule2 = targetSpecRules2.get(i);

            // assert rule1.getEnsures().equals(rule2.getEnsures());

            // TODO: split requires for each side and for both sides in createLhsPattern
            startSyncNodes1.add(startRule1.createLhsPattern(rewritingContext1, 1));
            startSyncNodes2.add(startRule2.createLhsPattern(rewritingContext2, 2));
            targetSyncNodes1.add(targetRule1.createLhsPattern(rewritingContext1, 1));
            targetSyncNodes2.add(targetRule2.createLhsPattern(rewritingContext2, 2));
            ensures.add(targetRule1.getRequires());

            // assert rule1.containsAttribute(Attribute.TRUSTED_KEY) == rule2.containsAttribute(Attribute.TRUSTED_KEY);
            trusted.add(startRule1.containsAttribute(Attribute.TRUSTED_KEY));
        }

        boolean result = SymbolicRewriter.equiv(startSyncNodes1, startSyncNodes2, targetSyncNodes1, targetSyncNodes2, ensures, trusted, rewriter1, rewriter2);
        System.out.println(result);

        return;
    }

    /**
     * compiledDef0: for parsing spec rules
     * compiledDef1: for symbolic execution
     */
    public static void kprove(CompiledDefinition compiledDef0, CompiledDefinition compiledDef1, String proofFile, String prelude) {

        GlobalOptions globalOptions = new GlobalOptions();
        KompileOptions kompileOptions = new KompileOptions();
        KRunOptions krunOptions = new KRunOptions();
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        KExceptionManager kem = new KExceptionManager(globalOptions);
        Stopwatch sw = new Stopwatch(globalOptions);
        FileUtil files = FileUtil.get(globalOptions, System.getenv());

        FileSystem fs = new PortableFileSystem(kem, files);
        Map<String, MethodHandle> hookProvider = HookProvider.get(kem); // new HashMap<>();
        InitializeRewriter.InitializeDefinition initializeDefinition = new InitializeRewriter.InitializeDefinition();

        //// setting options

        krunOptions.experimental.prove = proofFile;
        krunOptions.experimental.smt.smtPrelude = prelude;

        SMTOptions smtOptions = krunOptions.experimental.smt;

        //// creating rewritingContext

        GlobalContext initializingContextGlobal = new GlobalContext(fs, javaExecutionOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.INITIALIZING);
        TermContext initializingContext = TermContext.builder(initializingContextGlobal).freshCounter(0).build();
        org.kframework.backend.java.kil.Definition evaluatedDef0 = initializeDefinition.invoke(compiledDef0.executionModule(), kem, initializingContext.global());
        org.kframework.backend.java.kil.Definition evaluatedDef1 = initializeDefinition.invoke(compiledDef1.executionModule(), kem, initializingContext.global());

        GlobalContext rewritingContextGlobal = new GlobalContext(fs, javaExecutionOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);
        rewritingContextGlobal.setDefinition(evaluatedDef1);
        TermContext rewritingContext = TermContext.builder(rewritingContextGlobal).freshCounter(initializingContext.getCounterValue()).build();

        //// parse spec file

        Kompile kompile = new Kompile(kompileOptions, globalOptions, files, kem, sw, false);
        Module specModule = kompile.parseModule(compiledDef0, files.resolveWorkingDirectory(proofFile).getAbsoluteFile());

        scala.collection.Set<Module> alsoIncluded = Stream.of("K-TERM", "K-REFLECTION", RuleGrammarGenerator.ID_PROGRAM_PARSING)
                .map(mod -> compiledDef0.getParsedDefinition().getModule(mod).get())
                .collect(org.kframework.Collections.toSet());

        specModule = new JavaBackend(kem, files, globalOptions, kompileOptions)
                .stepsForProverRules()
                .apply(Definition.apply(specModule, org.kframework.Collections.add(specModule, alsoIncluded), Att.apply()))
                .getModule(specModule.name()).get();

        ExpandMacros macroExpander = new ExpandMacros(compiledDef0.executionModule(), kem, files, globalOptions, kompileOptions);

        List<Rule> specRules = stream(specModule.localRules())
                .filter(r -> r.toString().contains("spec.k"))
                .map(r -> (Rule) macroExpander.expand(r))
                .map(r -> ProofExecutionMode.transformFunction(JavaBackend::ADTKVariableToSortedVariable, r))
                .map(r -> ProofExecutionMode.transformFunction(JavaBackend::convertKSeqToKApply, r))
                .map(r -> ProofExecutionMode.transform(NormalizeKSeq.self(), r))
                        //.map(r -> kompile.compileRule(compiledDefinition, r))
                .collect(Collectors.toList());

        //// massage spec rules

        KOREtoBackendKIL converter = new KOREtoBackendKIL(compiledDef0.executionModule(), evaluatedDef0, rewritingContext.global(), false);
        List<org.kframework.backend.java.kil.Rule> javaRules = specRules.stream()
                .map(r -> converter.convert(Optional.<Module>empty(), r))
                .map(r -> new org.kframework.backend.java.kil.Rule(
                        r.label(),
                        r.leftHandSide().evaluate(rewritingContext),
                        r.rightHandSide().evaluate(rewritingContext),
                        r.requires(),
                        r.ensures(),
                        r.freshConstants(),
                        r.freshVariables(),
                        r.lookups(),
                        r.isCompiledForFastRewriting(),
                        r.lhsOfReadCell(),
                        r.rhsOfWriteCell(),
                        r.cellsToCopy(),
                        r.matchingInstructions(),
                        r,
                        rewritingContext.global()))
                .collect(Collectors.toList());
        List<org.kframework.backend.java.kil.Rule> allRules = javaRules.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());

        // rename all variables again to avoid any potential conflicts with the rules in the semantics
        javaRules = javaRules.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());

        //// prove spec rules

        SymbolicRewriter rewriter = new SymbolicRewriter(rewritingContextGlobal, kompileOptions, new KRunState.Counter(), converter);

        List<ConstrainedTerm> proofResults = javaRules.stream()
                .filter(r -> !r.containsAttribute(Attribute.TRUSTED_KEY))
                .map(r -> rewriter.proveRule(r.createLhsPattern(rewritingContext,1), r.createRhsPattern(1), allRules))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        //// print result

        //System.out.println(proofResults);

        List<K> result = proofResults.stream()
                .map(ConstrainedTerm::term)
                .map(t -> (KItem) t)
                .collect(Collectors.toList());

        System.out.println(result);
        return;
    }

}