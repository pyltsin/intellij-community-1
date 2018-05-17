// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CredentialPromptDialog")
package com.intellij.credentialStore

import com.intellij.CommonBundle
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.project.Project
import com.intellij.ui.AppIcon
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize
import javax.swing.JCheckBox
import javax.swing.JPasswordField

/**
 * @param project The context project (might be null)
 * @param dialogTitle The dialog title
 * @param passwordFieldLabel The password field label, describing a resource, for which password is asked
 * @param resetPassword if true, the old password is removed from database and new password will be asked.
 * @param error The error to show in the dialog
 * @return null if dialog was cancelled or password (stored in database or a entered by user)
 */
@JvmOverloads
fun askPassword(project: Project?,
                dialogTitle: String,
                passwordFieldLabel: String,
                attributes: CredentialAttributes,
                resetPassword: Boolean = false,
                error: String? = null): String? {
  return askCredentials(project, dialogTitle, passwordFieldLabel, attributes, resetPassword = resetPassword, error = error)?.credentials?.getPasswordAsString()?.nullize()
}

internal object RememberCheckBoxState {
  private const val key = "checkbox.remember.password"
  private const val defaultValue = true

  val isSelected: Boolean
    get() {
      return PropertiesComponent.getInstance().getBoolean(key, defaultValue)
    }

  fun update(component: JCheckBox) {
    PropertiesComponent.getInstance().setValue(key, component.isSelected, defaultValue)
  }
}

@JvmOverloads
fun askCredentials(project: Project?,
                   dialogTitle: String,
                   passwordFieldLabel: String,
                   attributes: CredentialAttributes,
                   isSaveOnOk: Boolean = true,
                   checkExistingBeforeDialog: Boolean = true,
                   resetPassword: Boolean = false,
                   error: String? = null): CredentialRequestResult? {
  val store = PasswordSafe.getInstance()
  if (resetPassword) {
    store.set(attributes, null)
  }
  else if (checkExistingBeforeDialog) {
    store.get(attributes)?.let {
      return CredentialRequestResult(it, false, true)
    }
  }

  return invokeAndWaitIfNeed(ModalityState.any()) {
    val passwordField = JPasswordField()
    val rememberCheckBox = if (store.isMemoryOnly) {
      null
    }
    else {
      CheckBox(CommonBundle.message("checkbox.remember.password"),
               selected = RememberCheckBoxState.isSelected,
               toolTip = "The password will be stored between application sessions.")
    }

    val panel = panel {
      row { label(if (passwordFieldLabel.endsWith(":")) passwordFieldLabel else "$passwordFieldLabel:") }
      row { passwordField() }
      rememberCheckBox?.let {
        row { it() }
      }
    }

    AppIcon.getInstance().requestAttention(project, true)
    if (!dialog(dialogTitle, project = project, panel = panel, focusedComponent = passwordField, errorText = error).showAndGet()) {
      return@invokeAndWaitIfNeed null
    }

    if (rememberCheckBox != null) {
      RememberCheckBoxState.update(rememberCheckBox)
    }

    val isMemoryOnly = store.isMemoryOnly || !rememberCheckBox!!.isSelected
    val credentials = Credentials(attributes.userName, passwordField.password.nullize())
    if (isSaveOnOk) {
      store.set(attributes, credentials, isMemoryOnly)
      credentials.getPasswordAsString()
    }
    return@invokeAndWaitIfNeed CredentialRequestResult(credentials, isMemoryOnly, false)
  }
}

data class CredentialRequestResult(val credentials: Credentials, val isMemoryOnly: Boolean, val isSaved: Boolean)