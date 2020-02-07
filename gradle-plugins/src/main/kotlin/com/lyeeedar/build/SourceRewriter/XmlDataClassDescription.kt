package com.lyeeedar.build.SourceRewriter

class XmlDataClassDescription(val name: String, val superClass: String, val classIndentation: Int, val classDefinition: ClassDefinition, val classRegister: ClassRegister, val annotations: ArrayList<AnnotationDescription>)
{
    val variables = ArrayList<VariableDescription>()

	val classContents = ArrayList<String>()

	val dataClassName: String
	val dataClassCategory: String
	val forceGlobal: Boolean

    init
    {
        classDefinition.classDef = this

		val dataClassAnnotation = annotations.firstOrNull { it.name == "DataClass" }
		if (dataClassAnnotation != null)
		{
			dataClassName = dataClassAnnotation.paramMap["name"] ?: name.capitalize()
			dataClassCategory = dataClassAnnotation.paramMap["category"] ?: ""
			forceGlobal = dataClassAnnotation.paramMap["global"] == "true"
		}
		else
		{
			dataClassName = name.capitalize()
			dataClassCategory = ""
			forceGlobal = false
		}
    }

    fun resolveImports(imports: HashSet<String>)
    {
        for (variable in variables)
        {
            variable.resolveImports(imports, classDefinition, classRegister)
        }

        if (classDefinition.isAbstract)
        {
            for (childClass in classDefinition.inheritingClasses)
            {
                if (!childClass.isAbstract)
                {
                    if (childClass.packageStr != classDefinition.packageStr)
                    {
                        imports.add(childClass.packageStr.replace("package ", "").trim() + ".${childClass.name}")
                    }
                }
            }
        }
    }

    fun write(builder: IndentedStringBuilder)
    {
        for (annotation in annotations)
        {
            builder.appendln(classIndentation, annotation.annotationString)
        }

        val classType = if (classDefinition.isAbstract) "abstract class" else "class"
        builder.appendln(classIndentation, "$classType $name : $superClass")
        builder.appendln(classIndentation, "{")

		// remove blank lines from end of content
		for (i in classContents.size-1 downTo 0)
		{
			if (classContents[i].isBlank()) classContents.removeAt(i)
			else break
		}

		for (line in classContents)
		{
			builder.appendln(classIndentation+1, line)
		}

        builder.appendln("")
        builder.appendln(classIndentation+1, "override fun load(xmlData: XmlData)")
        builder.appendln(classIndentation+1, "{")

        if (classDefinition.superClass != null && classDefinition.superClass!!.name != "XmlDataClass")
        {
            builder.appendln(classIndentation+2, "super.load(xmlData)")
        }

        for (variable in variables)
        {
            variable.writeLoad(builder, classIndentation+2, classDefinition, classRegister)
        }

        builder.appendln(classIndentation+1, "}")

        if (classDefinition.isAbstract)
        {
            builder.appendln("")

            // write switch loader
            builder.appendln(classIndentation+1, "companion object")
            builder.appendln(classIndentation+1, "{")

            builder.appendln(classIndentation+2, "fun loadPolymorphicClass(classID: String): $name")
            builder.appendln(classIndentation+2, "{")

            builder.appendln(classIndentation+3, "return when (classID)")
            builder.appendln(classIndentation+3, "{")

            for (childClass in classDefinition.inheritingClasses)
            {
                if (!childClass.isAbstract)
                {
                    builder.appendln(classIndentation+4, "${childClass.classID} -> ${childClass.name}()")
                }
            }

            builder.appendln(classIndentation+4, "else -> throw RuntimeException(\"Unknown classID '\$classID' for $name!\")")
            builder.appendln(classIndentation+3, "}")

            builder.appendln(classIndentation+2, "}")

            builder.appendln(classIndentation+1, "}")
        }

        builder.appendln(classIndentation, "}")
    }

    fun createDefFile(builder: IndentedStringBuilder, needsGlobalScope: Boolean)
    {
		val extends = if (classDefinition.superClass?.superClass != null) "Extends=\"${classDefinition.superClass!!.classDef!!.dataClassName}\"" else ""

        val dataFileAnnotation = annotations.firstOrNull { it.name == "DataFile" }
        if (dataFileAnnotation != null)
        {
            builder.appendln(1, """<Definition Name="$dataClassName" $extends meta:RefKey="Struct">""")
        }
        else
        {
            val global = if (needsGlobalScope) "IsGlobal=\"True\"" else ""
            builder.appendln(1, """<Definition Name="$dataClassName" $global $extends meta:RefKey="StructDef">""")
        }

        for (variable in variables)
        {
			if (variable.raw.startsWith("abstract")) continue
            variable.createDefEntry(builder, classRegister)
        }

        builder.appendln(1, "</Definition>")
    }
}