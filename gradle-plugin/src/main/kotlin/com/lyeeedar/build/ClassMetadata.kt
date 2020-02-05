package com.lyeeedar.build

import jdk.internal.org.objectweb.asm.tree.*
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import java.util.*

class ClassMetadata
{
	var type: Type? = null
	var superClass: String? = null

	private val methods = HashMap<String, MethodDescriptor>()
	private val fields = HashMap<String, FieldDescriptor>()

	fun addMethod(methodDescriptor: MethodDescriptor)
	{
		val key = "${methodDescriptor.name}:::${methodDescriptor.desc}"
		methods.put(key, methodDescriptor)
	}

	fun field(name: String): FieldDescriptor
	{
		var existing = fields[name]
		if (existing == null)
		{
			existing = FieldDescriptor(name)
			fields.put(name, existing)
		}

		return existing
	}

	fun method(name: String, desc: String): MethodDescriptor?
	{
		return methods["$name:::$desc"]
	}
}


class MethodDescriptor(val access: Int, val name: String, val desc: String, val signature: String?, val exceptions: Array<String>?)
{
	override fun toString(): String
	{
		return "MethodDescriptor[" +
			   ", name='" + name + '\'' +
			   ", desc='" + desc + '\'' +
			   ", signature='" + signature + '\'' +
			   ']'
	}

}

class FieldDescriptor(var name: String)
{
	var access = 0
	var desc: String? = null
	var signature: String? = null
	var value: Any? = null
	var reset: AbstractInsnNode? = null
	var entityLinkMutator: Class<*>? = null

	operator fun set(access: Int, desc: String?, signature: String?, value: Any?)
	{
		this.access = access
		this.desc = desc
		this.signature = signature
		this.value = value
	}

	fun isResettable(): Boolean
	{ // desc == null; means we're actually dealing with a reference to a field
		// in a parent class; tricky... but it mostly affects packed components, so
		// we'll leave it here for now
		return desc != null && (desc!!.length == 1 || "Ljava/lang/String;" == desc)
	}

	override fun toString(): String
	{
		return "FieldDescriptor{" +
			   "name='" + name + '\'' +
			   ", desc='" + desc + '\'' +
			   ", signature='" + signature + '\'' +
			   '}'
	}

}

class MetaScanner(private val info: ClassMetadata) : ClassVisitor(ASM4), Opcodes
{
	private class AnnotationReader private constructor(av: AnnotationVisitor, private val info: ClassMetadata) : AnnotationVisitor(ASM4, av)
	{
		override fun visit(name: String, value: Any)
		{
			if ("forceWeaving" == name)
			{
			//	info.forcePooledWeaving = value as Boolean
			}
			super.visit(name, value)
		}

	}

	override fun visit(version: Int,
			  access: Int,
			  name: String?,
			  signature: String?,
			  superName: String,
			  interfaces: Array<String?>?)
	{
		info.superClass = superName
		super.visit(version, access, name, signature, superName, interfaces)
	}

	override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor
	{
		var av: AnnotationVisitor = super.visitAnnotation(desc, visible)
//		if (POOLED_ANNOTATION.equals(desc))
//		{
//			info.annotation = WeaverType.POOLED
//			av = AnnotationReader(av, info)
//		}
//		else if (PROFILER_ANNOTATION.equals(desc))
//		{
//			return ProfileAnnotationReader(desc, info)
//		}
//		else if (WOVEN_ANNOTATION.equals(desc))
//		{
//			info.isPreviouslyProcessed = true
//		}
//		else if (info.sysetemOptimizable === OptimizationType.FULL
//				 && Weaver.PRESERVE_VISIBILITY_ANNOTATION.equals(desc))
//		{
//			info.sysetemOptimizable = OptimizationType.SAFE
//		}
		return av
	}

	override fun visitField(access: Int,
				   name: String?,
				   desc: String,
				   signature: String,
				   value: Any?): FieldVisitor?
	{
		val field = info.field(name!!)
		field[access, desc, signature] = value
		var fv: FieldVisitor = super.visitField(access, name, desc, signature, value)
//		if ("Lcom/artemis/Entity;" == desc)
//		{
//			field.entityLinkMutator = UniEntityLink.Mutator::class.java
//		}
//		else if ("I" == desc)
//		{
//			fv = EntityIdScanVisitor(fv, field, UniEntityIdLink.Mutator::class.java)
//		}
//		else if ("Lcom/artemis/utils/IntBag;" == desc)
//		{
//			fv = EntityIdScanVisitor(fv, field, MultiEntityIdLink.Mutator::class.java)
//		}
//		else if ("Lcom/artemis/utils/Bag<Lcom/artemis/Entity;>;" == signature)
//		{
//			field.entityLinkMutator = MultiEntityLink.Mutator::class.java
//		}
		if (field.isResettable()) field.reset = constInstructionFor(field)
		return fv
	}

	override fun visitMethod(access: Int,
					name: String,
					desc: String,
					signature: String?,
					exceptions: Array<String>?): MethodVisitor
	{
		val mv: MethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

		info.addMethod(MethodDescriptor(access, name, desc, signature, exceptions))

//		if ("reset" == name && "()V" == desc) info.foundReset = true
//		else if ("begin" == name && "()V" == desc) info.foundBegin = true
//		else if ("end" == name && desc == "()V") info.foundEnd = true
//		else if ("initialize" == name && "()V" == desc) info.foundInitialize = true

		if ("<init>" == name && "()V" == desc)
		{
			return DefaultValueScanner(mv, info)
		}
		else
		{
			return mv
		}
	}

	companion object
	{
		private fun constInstructionFor(field: FieldDescriptor): AbstractInsnNode
		{
			if ("Ljava/lang/String;" == field.desc) return InsnNode(ACONST_NULL)
			when (field.desc!![0])
			{
				'Z', 'B', 'C', 'S', 'I' -> return InsnNode(ICONST_0)
				'J' -> return InsnNode(LCONST_0)
				'F' -> return InsnNode(FCONST_0)
				'D' -> return InsnNode(DCONST_0)
			}
			throw RuntimeException(field.toString())
		}
	}
}

class DefaultValueScanner(parent: MethodVisitor?, private val meta: ClassMetadata) : MethodVisitor(ASM5, parent), Opcodes
{
	private var node: AbstractInsnNode? = null
	override fun visitVarInsn(opcode: Int, `var`: Int)
	{
		node = VarInsnNode(opcode, `var`)
		super.visitVarInsn(opcode, `var`)
	}

	override fun visitLdcInsn(cst: Any?)
	{
		node = LdcInsnNode(cst)
		super.visitLdcInsn(cst)
	}

	override fun visitInsn(opcode: Int)
	{
		node = InsnNode(opcode)
		super.visitInsn(opcode)
	}

	override fun visitIntInsn(opcode: Int, operand: Int)
	{
		node = IntInsnNode(opcode, operand)
		super.visitIntInsn(opcode, operand)
	}

	override fun visitFieldInsn(opcode: Int, owner: String, name: String?, desc: String?)
	{
		if (meta.type!!.internalName == owner)
		{
			val fd = meta.field(name!!)
			if (fd.isResettable())
			{
				fd.reset = node
				node = null
			}
		}
		super.visitFieldInsn(opcode, owner, name, desc)
	}

}