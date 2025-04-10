import com.android.build.gradle.internal.tasks.R8Task
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.grappenmaker.mappings.*
import kotlinx.serialization.json.*
import java.io.IOException

plugins {
    id("com.android.application")
}

private class DexBackedInheritanceProvider(file: DexBackedDexFile) : InheritanceProvider {
    private fun String.dropDescriptor() = substring(1, length - 1)
    private val classesByName = file.classes.associateBy { it.type.dropDescriptor() }

    override fun getDeclaredMethods(internalName: String, inheritable: Boolean): Iterable<String> {
        return (classesByName[internalName] ?: return emptyList()).methods.asSequence()
            .let { if (inheritable) it.filter { m -> m.accessFlags and INHERITABLE_MASK == 0 } else it }
            .map { "${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}" }.asIterable()
    }

    override fun getDirectParents(internalName: String): Iterable<String> {
        val def = classesByName[internalName] ?: return emptyList()

        return sequence {
            def.superclass?.let { yield(it.dropDescriptor()) }
            yieldAll(def.interfaces.asSequence().map { it.dropDescriptor() })
        }.asIterable()
    }
}

abstract class CreateCompactedMappings @Inject constructor() : DefaultTask() {
    @get:InputDirectory
    abstract val outputDex: DirectoryProperty

    @get:InputFile
    abstract val mappingsFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun create() {
        val dexFilePath = outputDex.get().asFile.walk().drop(1).single()
        val dexFile = DexFileFactory.loadDexFile(dexFilePath, null)
        val mappingsFilePath = mappingsFile.get().asFile
        val mappings = mappingsFilePath.useLines { ProguardMappingsFormat.parse(it) }
        val provider = DexBackedInheritanceProvider(dexFile)

        mappings
            .reorderNamespaces("official", "named")
            .filterClasses { !it.names.first().startsWith("R8\$\$REMOVED\$\$CLASS\$\$") }
            .restoreResidualSignatures()
            .removeUselessAccessors()
            .removeComments()
            .removeRedundancy(provider, removeDuplicateMethods = true)
            .asCompactedMappings()
            .writeTo(outputFile.asFile.get().outputStream())
    }

    private fun Mappings.removeUselessAccessors() = mapClasses { it.removeUselessAccessors() }

    private fun MappedClass.removeUselessAccessors(): MappedClass {
        if (methods.size < 2) return this
        val deobfedSignatures = methods.mapTo(hashSetOf()) { it.names.last() + it.desc }

        return filterMethods { method ->
            val name = method.names.last()
            name.length < 8 || // not possibly named accessor
                    !name.startsWith("access\$") || // not accessor
                    name[7].isDigit() || // not static accessor (JvmStatic)
                    (name.drop(8) + method.desc) !in deobfedSignatures // not same signature human-readable
        }
    }

    private fun MappedMethod.restoreResidualSignature(): MappedMethod {
        if (comments.isEmpty()) return this

        val attributes = comments.mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val target = attributes.find {
            it["id"]?.jsonPrimitive?.contentOrNull == "com.android.tools.r8.residualsignature"
        } ?: return this

        val signature = target["signature"]?.jsonPrimitive?.contentOrNull ?: return this
        return if (desc != signature) copy(desc = signature) else this
    }

    private fun Mappings.restoreResidualSignatures(): Mappings = mapMethods { _, m -> m.restoreResidualSignature() }
}

tasks {
    afterEvaluate {
        val minifyReleaseWithR8 by getting(R8Task::class)
        val createCompactedMappings by registering(CreateCompactedMappings::class) {
            group = "compacted mappings"
            dependsOn(minifyReleaseWithR8)

            outputDex = minifyReleaseWithR8.outputDex
            mappingsFile = minifyReleaseWithR8.mappingFile
            outputFile = layout.buildDirectory.get().dir("outputs").dir("mapping").file("mapping.compact")
        }

        val compressCompactedMappings by registering {
            group = "compacted mappings"

            dependsOn(createCompactedMappings)
            val uncompressedFile = createCompactedMappings.get().outputs.files.singleFile.absolutePath

            doLast {
                // Using try-catch to allow failure, when xz is not on path
                // TODO: do we force xz to be on path?
                // Using ProcessBuilder such that the task is cacheable
                // exec {} has a receiver on Project which is never cacheable
                try {
                    ProcessBuilder("xz", "-k", "-9", "-f", "-e", uncompressedFile)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start().waitFor()
                } catch (e: IOException) {
                    logger.warn("Could not compress mappings, XZ might not be on PATH")
                    logger.trace("Compressing mappings failed", e)
                }
            }
        }
    }
}