import com.google.kotlin.debuginfo.generateComparisonTests
import com.google.kotlin.debuginfo.generateManualTests
import com.google.kotlin.debuginfo.index.SourceIndex
import com.google.kotlin.debuginfo.model.MethodInfo
import com.google.kotlin.debuginfo.model.MethodInfoPair
import com.google.kotlin.debuginfo.parser.extractMethodInfoDex
import com.google.kotlin.debuginfo.parser.extractMethodInfoJvm
import java.io.File

val KOTLIN_METADATA_INTERNAL_NAME = "Lkotlin/Metadata;"

val comparisonTests = listOf(
  "CandidateResolver" to "checkValueArgumentTypes",
  "ClassTranslator" to "generateSecondaryConstructor",
  "CoroutineTransformerMethodVisitorKt" to "updateLvtAccordingToLiveness",
  "DelegatedPropertyGenerator" to "generateDelegatedProperty",
  "ExposedVisibilityChecker" to "checkFunction",
  "FirClassSubstitutionScope" to "createSubstitutionOverrideProperty",
  "FirMemberPropertiesChecker" to "checkProperty",
  "FirSupertypesChecker" to "check",
  "KtLightSourceElement" to "hashCode",
  "MainMethodGenerationLowering" to "irRunSuspend",
  "ToArrayLowering\$lower\$2" to "invoke",
  "WobblyTF8" to "encode",
)

// this method doesn't even have locals other than the parameters...
//  "IncrementalJvmCompilerRunner" to "calculateSourcesToCompileImpl",
val breakpointTests = listOf(
  "ClassTranslator" to "emitConstructors",
  "ConeCapturedType" to "hashCode",
  "DeprecationResolver" to "getDeprecationByVersionRequirement",
  "FastJarHandler" to "contentsToByteArray",
  "FirJavaFacade" to "createFirJavaClass",
  "FirRepeatableAnnotationChecker" to "check",
  "FunctionDescriptorResolver" to "initializeFunctionDescriptorAndExplicitReturnType",
  "IrOverridingUtil" to "buildFakeOverridesForClassUsingOverriddenSymbols",
  "K2JSDce" to "copyResource",
  "NewResolutionOldInference" to "convertToOverloadResults",
  "SingleAbstractMethodLowering" to "createObjectProxy",
)

// Complicated methods which fail the breakpoint lvt test but may pass the inline call stack comparison test.
//  "BodyResolveContext" to "withField", // wrong line number for call site
//  "FinallyBlocksLowering" to "visitTry", // wrong line number for call site
//  "IrDeclarationDeserializer" to "deserializeIrFunction\$ir_serialization_common", // utterly broken
//  "ZipUtilKt" to "withZipFileSystem", // wrong breakpoint

// Missing debug info:
// GenericReplCompilingEvaluatorBase.compileAndEval

val inlineCallStackTests = listOf(
  "BaseFirBuilder" to "generateSetGetBlockForAugmentedArraySetCall",
  "ClassDeserializationKt" to "deserializeClassToSymbol",
  "CollectionStubMethodLowering" to "computeStubsForSuperClass",
  "FirDefaultSimpleImportingScope" to "<init>",
  "FirJvmOverridesBackwardCompatibilityHelper" to "isPlatformSpecificSymbolThatCanBeImplicitlyOverridden",
  "FirSyntheticPropertiesScope\$hasJavaOverridden\$1" to "invoke",
  "IrArrayBuilder" to "buildComplexArray",
  "JavaClassUseSiteMemberScope" to "shouldBeVisibleAsOverrideOfBuiltInWithErasedValueParameters",
  "JvmAnnotationImplementationTransformer" to "kClassArrayToJClassArray",
  "MemoizedInlineClassReplacements\$createStaticReplacement\$1" to "invoke",
  "SyntheticFunctionalInterfaceCache" to "createSyntheticFunctionalInterface",
  "WasmCompiledModuleFragment" to "linkWasmCompiledFragments",
)

val manualInlineCallStackTests = listOf(
  "AbstractSuspendFunctionsLowering\$addMissingSupertypesToSuspendFunctionImplementingClasses\$1" to "addMissingSupertypes",
  "FinallyBlocksLowering" to "visitTry",
  "FirDeclarationsResolveTransformer" to "transformField",
  "FirDeclarationsResolveTransformer" to "transformProperty",
  "ScriptGenerator" to "generateScriptDeclaration",
  "TopDownAnalyzerFacadeForJVM" to "createContainer",
  "ZipUtilKt" to "withZipFileSystem",
)
// Where is this code even from? Some SAM converted lambda?
//  "IrDeclarationDeserializer" to "deserializeIrFunction\$ir_serialization_common",

private val MethodInfo.key: String
  get() = "${classInfo.thisClass}.$name$descriptor"

fun findMethodInfos(
  methods: List<MethodInfoPair>,
  specs: List<Pair<String, String>> // pairs of class name and method name
): List<MethodInfoPair> =
  methods.filter { method ->
    specs.any { (thisClass, methodName) ->
      method.jvm!!.classInfo.thisClass.substringAfterLast('.') == thisClass &&
        method.jvm!!.name == methodName
    }
  }.distinctBy { method ->
    method.jvm!!.classInfo.thisClass.substringAfterLast('.').substringBefore('$') +
      method.jvm!!.name.substringBefore('$')
  }.sortedBy { method ->
    method.jvm!!.localVariableTable.size
  }

fun main(args: Array<String>) {
  val kotlinIndex = if (args.isNotEmpty()) SourceIndex(File(args[0])) else null

  val jvmMethodInfos = File("data/kotlin-compiler-jvm").walk().filter {
    it.extension == "class"
  }.flatMapTo(mutableListOf(), ::extractMethodInfoJvm)

  val dexMethodInfos = File("data/kotlin-compiler-dex").walk().filter {
    it.extension == "dex"
  }.flatMapTo(mutableListOf(), ::extractMethodInfoDex)

  println("#jvm-methods: ${jvmMethodInfos.size}")
  println("#dex-methods: ${dexMethodInfos.size}")

  // Match up the methods on both sides
  val jvmToDexMap = mutableMapOf<String, MethodInfoPair>()
  for (methodInfo in jvmMethodInfos) {
    jvmToDexMap.getOrPut(methodInfo.key) { MethodInfoPair() }.jvm = methodInfo
  }
  for (methodInfo in dexMethodInfos) {
    jvmToDexMap.getOrPut(methodInfo.key) { MethodInfoPair() }.dex = methodInfo
  }

  val matchedMethodInfos = jvmToDexMap.values.filter { it.jvm != null && it.dex != null }
  println("#matched-methods: ${matchedMethodInfos.size}")

  generateComparisonTests(
    findMethodInfos(matchedMethodInfos, comparisonTests),
    "DexLocalVariableTableComparisonTest",
    "doLocalVariableTableComparisonTest",
    kotlinIndex
  )

  generateComparisonTests(
    findMethodInfos(matchedMethodInfos, breakpointTests),
    "DexLocalVariableTableBreakpointTest",
    "doLocalVariableTableBreakpointComparisonTest",
    kotlinIndex
  )

  generateComparisonTests(
    findMethodInfos(matchedMethodInfos, inlineCallStackTests),
    "DexInlineCallStackComparisonTest",
    "doInlineCallStackComparisonTest",
    kotlinIndex
  )

  generateManualTests(
    findMethodInfos(matchedMethodInfos, manualInlineCallStackTests),
    "KotlinInlineCallStackTest",
    kotlinIndex
  )

  if (kotlinIndex != null) {
    File("generated/build.txt").writeText(kotlinIndex.commitHash)
  }
}
