// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.ModuleExtension
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder

/**
 * Register implementation of this interface as `com.intellij.workspaceModel.moduleExtensionBridgeFactory` extension to provide implementations
 * of [ModuleExtension] based on data from workspace model.
 */
interface ModuleExtensionBridgeFactory {
  //this is temporary needed until we get rid of the old implementation of project model interfaces
  val originalExtensionType: Class<out ModuleExtension>
  fun createExtension(module: ModuleBridge, entityStorage: VersionedEntityStorage, diff: WorkspaceEntityStorageDiffBuilder?): ModuleExtensionBridge
}