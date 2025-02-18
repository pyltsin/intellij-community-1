// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.test

import com.intellij.externalSystem.JavaProjectData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.test.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.pom.java.LanguageLevel

fun project(name: String = "project",
            projectPath: String,
            systemId: ProjectSystemId = TEST_EXTERNAL_SYSTEM_ID,
            init: Project.() -> Unit) = Project().also {
  it.name = name
  it.projectPath = projectPath
  it.systemId = systemId
  it.init()
}

interface Node {
  fun render(builder: StringBuilder, indent: String)
}

abstract class AbstractNode<DataType : Any?>(val type: String) : Node {
  lateinit var systemId: ProjectSystemId
  protected lateinit var parent: AbstractNode<*>
  val children = arrayListOf<Node>()
  val props = hashMapOf<String, String>()

  protected fun <T : Node> initChild(node: T, init: T.() -> Unit): T {
    (node as AbstractNode<*>).systemId = this.systemId
    (node as AbstractNode<*>).parent = this
    children.add(node)
    node.init()
    return node
  }

  fun <T : Any> ext(key: com.intellij.openapi.externalSystem.model.Key<T>, model: T, init: T.() -> Unit = {}) =
    initChild(OtherNode(key, model)) { init.invoke(model) }

  abstract fun createDataNode(parentData: Any? = null): DataNode<DataType>

  override fun render(builder: StringBuilder, indent: String) {
    builder.append("$indent<$type${renderProps()}")
    if (children.isNotEmpty()) {
      builder.appendLine(">")
      children.forEach { it.render(builder, "$indent  ") }
      builder.appendLine("$indent</$type>")
    }
    else {
      builder.appendLine("/>")
    }
  }

  private fun renderProps(): String {
    val builder = StringBuilder()
    for ((attr, value) in props) {
      builder.append(" $attr=\"$value\"")
    }
    return builder.toString()
  }

  override fun toString() = StringBuilder().also { render(it, "") }.toString()
}

abstract class NamedNode<T : Any>(type: String) : AbstractNode<T>(type) {
  var name: String
    get() = props["name"]!!
    set(value) {
      props["name"] = value
    }
}

class Project : NamedNode<ProjectData>("project") {
  var projectPath: String
    get() = props["projectPath"]!!
    set(value) {
      props["projectPath"] = value
    }

  fun module(name: String = "module",
             externalProjectPath: String = projectPath,
             moduleFilePath: String? = null,
             init: Module.() -> Unit = {}) =
    initChild(Module()) {
      this.name = name
      this.moduleFileDirectoryPath = moduleFilePath ?: projectPath
      this.externalProjectPath = externalProjectPath
      init.invoke(this)
    }

  fun javaProject(compileOutputPath: String,
                  languageLevel: LanguageLevel? = null,
                  targetBytecodeVersion: String? = null) =
    initChild(JavaProject()) {
      this.compileOutputPath = compileOutputPath
      this.languageLevel = languageLevel
      this.targetBytecodeVersion = targetBytecodeVersion
    }

  override fun createDataNode(parentData: Any?): DataNode<ProjectData> {
    val projectData = ProjectData(systemId, name, projectPath, projectPath)
    return DataNode(ProjectKeys.PROJECT, projectData, null)
  }
}

class Module : NamedNode<ModuleData>("module") {
  var externalProjectPath: String
    get() = props["externalProjectPath"]!!
    set(value) {
      props["externalProjectPath"] = value
    }
  var moduleFileDirectoryPath: String
    get() = props["moduleFileDirectoryPath"]!!
    set(value) {
      props["moduleFileDirectoryPath"] = value
    }

  fun module(name: String = "module",
             externalProjectPath: String,
             moduleFilePath: String? = null,
             init: Module.() -> Unit = {}) =
    initChild(Module()) {
      this.name = name
      this.moduleFileDirectoryPath = moduleFilePath ?: externalProjectPath
      this.externalProjectPath = externalProjectPath
      init.invoke(this)
    }

  fun contentRoot(path: String = externalProjectPath, init: ContentRoot.() -> Unit = {}) =
    initChild(ContentRoot()) {
      this.root = path
      init.invoke(this)
    }

  fun lib(name: String, level: LibraryLevel = LibraryLevel.PROJECT, unresolved: Boolean = false, init: Lib.() -> Unit = {}) =
    initChild(Lib()) {
      this.name = name
      this.level = level
      this.unresolved = unresolved
      init.invoke(this)
    }

  override fun createDataNode(parentData: Any?): DataNode<ModuleData> {
    val moduleData = ModuleData(name, systemId, ModuleTypeId.JAVA_MODULE, name, moduleFileDirectoryPath, externalProjectPath)
    return DataNode(ProjectKeys.MODULE, moduleData, null)
  }
}

class ContentRoot : AbstractNode<ContentRootData>("contentRoot") {
  var root: String
    get() = props["root"]!!
    set(value) {
      props["root"] = value
    }
  val folders = LinkedHashMap<ExternalSystemSourceType, MutableList<ContentRootData.SourceRoot>>()

  fun folder(type: ExternalSystemSourceType, relativePath: String, packagePrefix: String? = null) {
    folders.computeIfAbsent(type) { ArrayList() }.add(ContentRootData.SourceRoot("$root/$relativePath", packagePrefix))
  }

  override fun createDataNode(parentData: Any?): DataNode<ContentRootData> {
    val contentRootData = ContentRootData(systemId, root)
    for ((type, list) in folders) {
      list.forEach { contentRootData.storePath(type, it.path, it.packagePrefix) }
    }
    return DataNode(ProjectKeys.CONTENT_ROOT, contentRootData, null)
  }
}

class Lib : NamedNode<LibraryDependencyData>("lib") {
  var level: LibraryLevel
    get() = LibraryLevel.valueOf(props["level"]!!)
    set(value) {
      props["level"] = value.name
    }
  var unresolved: Boolean
    get() = props["unresolved"]!!.toBoolean()
    set(value) {
      props["unresolved"] = value.toString()
    }
  val roots = LinkedHashMap<LibraryPathType, MutableList<String>>()

  fun roots(type: LibraryPathType, vararg roots: String) {
    this.roots.computeIfAbsent(type) { ArrayList() }.addAll(roots)
  }

  override fun createDataNode(parentData: Any?): DataNode<LibraryDependencyData> {
    check(parentData is ModuleData)
    val libraryData = LibraryData(systemId, name, unresolved)
    for ((type, list) in roots) {
      list.forEach { libraryData.addPath(type, it) }
    }
    val libraryDependencyData = LibraryDependencyData(parentData, libraryData, level)
    return DataNode(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData, null)
  }
}

class JavaProject : AbstractNode<JavaProjectData>("javaProject") {
  var compileOutputPath: String
    get() = props["compileOutputPath"]!!
    set(value) {
      props["compileOutputPath"] = value
    }
  var languageLevel: LanguageLevel?
    get() = props["languageLevel"]?.run { LanguageLevel.valueOf(this) }
    set(value) {
      if (value == null) props.remove("languageLevel")
      else props["languageLevel"] = value.name
    }
  var targetBytecodeVersion: String?
    get() = props["targetBytecodeVersion"]
    set(value) {
      if (value == null) props.remove("targetBytecodeVersion")
      else props["targetBytecodeVersion"] = value
    }

  override fun createDataNode(parentData: Any?): DataNode<JavaProjectData> {
    val javaProjectData = JavaProjectData(systemId, compileOutputPath, languageLevel, targetBytecodeVersion)
    return DataNode(JavaProjectData.KEY, javaProjectData, null)
  }
}

class OtherNode<T : Any>(private val key: com.intellij.openapi.externalSystem.model.Key<T>,
                         private val model: T) : AbstractNode<T>(key.dataType) {
  override fun createDataNode(parentData: Any?): DataNode<T> {
    return DataNode(key, model, null)
  }
}

fun <DataType : Any?> AbstractNode<DataType>.toDataNode(parentData: Any? = null): DataNode<DataType> {
  val dataNode = createDataNode(parentData)
  for (child in children) {
    val node = child as AbstractNode<*>
    dataNode.addChild(node.toDataNode(dataNode.data))
  }
  return dataNode
}
