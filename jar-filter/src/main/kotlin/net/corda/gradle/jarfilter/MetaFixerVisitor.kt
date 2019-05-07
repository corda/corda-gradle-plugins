package net.corda.gradle.jarfilter

import org.gradle.api.logging.Logger
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*

/**
 * ASM [ClassVisitor] for the MetaFixer task. This visitor inventories every function,
 * property and inner class within the byte-code and then passes this information to
 * the [MetaFixerTransformer].
 */
class MetaFixerVisitor private constructor(
    visitor: ClassVisitor,
    logger: Logger,
    kotlinMetadata: MutableMap<String, List<String>>,
    private val classNames: Set<String>,
    private val fields: MutableSet<FieldElement>,
    private val methods: MutableSet<String>,
    private val nestedClasses: MutableSet<String>
) : KotlinAfterProcessor(ASM7, visitor, logger, kotlinMetadata), Repeatable<MetaFixerVisitor> {
    constructor(visitor: ClassVisitor, logger: Logger, classNames: Set<String>)
        : this(visitor, logger, mutableMapOf(), classNames, mutableSetOf(), mutableSetOf(), mutableSetOf())

    override fun recreate(visitor: ClassVisitor) = MetaFixerVisitor(visitor, logger, kotlinMetadata, classNames, fields, methods, nestedClasses)

    private var className: String = "(unknown)"

    override fun visit(version: Int, access: Int, clsName: String, signature: String?, superName: String?, interfaces: Array<String>?) {
        className = clsName
        logger.info("Class {}", clsName)
        super.visit(version, access, clsName, signature, superName, interfaces)
    }

    override fun visitField(access: Int, fieldName: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
        if (fields.add(FieldElement(fieldName, descriptor))) {
            logger.info("- field {},{}", fieldName, descriptor)
        }
        return super.visitField(access, fieldName, descriptor, signature, value)
    }

    override fun visitMethod(access: Int, methodName: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
        if (methods.add(methodName + descriptor)) {
            logger.info("- method {}{}", methodName, descriptor)
        }
        return super.visitMethod(access, methodName, descriptor, signature, exceptions)
    }

    override fun visitInnerClass(clsName: String, outerName: String?, innerName: String?, access: Int) {
        if (outerName == className && innerName != null && nestedClasses.add(innerName)) {
            logger.info("- inner class {}", clsName)
        }
        return super.visitInnerClass(clsName, outerName, innerName, access)
    }

    override fun processClassMetadata(data1: List<String>, data2: List<String>): List<String> {
        return ClassMetaFixerTransformer(
                logger = logger,
                actualFields = fields,
                actualMethods = methods,
                actualNestedClasses = nestedClasses,
                actualClasses = classNames,
                data1 = data1,
                data2 = data2)
            .transform()
    }

    override fun processPackageMetadata(data1: List<String>, data2: List<String>): List<String> {
        return PackageMetaFixerTransformer(
                logger = logger,
                actualFields = fields,
                actualMethods = methods,
                data1 = data1,
                data2 = data2)
            .transform()
    }
}
