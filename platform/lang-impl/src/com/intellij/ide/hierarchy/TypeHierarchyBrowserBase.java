// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.hierarchy;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class TypeHierarchyBrowserBase extends HierarchyBrowserBaseEx {
  public static final String TYPE_HIERARCHY_TYPE = "Class {0}";
  public static final String SUBTYPES_HIERARCHY_TYPE = "Subtypes of {0}";
  public static final String SUPERTYPES_HIERARCHY_TYPE = "Supertypes of {0}";

  private boolean myIsInterface;

  private final MyDeleteProvider myDeleteElementProvider = new MyDeleteProvider();

  public TypeHierarchyBrowserBase(Project project, PsiElement element) {
    super(project, element);
  }

  protected abstract boolean isInterface(@NotNull PsiElement psiElement);

  protected void createTreeAndSetupCommonActions(@NotNull Map<? super String, ? super JTree> trees, @NotNull String groupId) {
    BaseOnThisTypeAction baseOnThisTypeAction = createBaseOnThisAction();
    JTree tree1 = createTree(true);
    PopupHandler.installPopupMenu(tree1, groupId, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP);
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree1);
    trees.put(getTypeHierarchyType(), tree1);

    JTree tree2 = createTree(true);
    PopupHandler.installPopupMenu(tree2, groupId, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP);
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree2);
    trees.put(getSupertypesHierarchyType(), tree2);

    JTree tree3 = createTree(true);
    PopupHandler.installPopupMenu(tree3, groupId, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP);
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree3);
    trees.put(getSubtypesHierarchyType(), tree3);
  }

  @NotNull
  protected BaseOnThisTypeAction createBaseOnThisAction() {
    return new BaseOnThisTypeAction();
  }

  protected abstract boolean canBeDeleted(PsiElement psiElement);

  protected abstract String getQualifiedName(PsiElement psiElement);

  @Override
  protected @NotNull Map<String, Supplier<String>> getPresentableNameMap() {
    HashMap<String, Supplier<String>> map = new HashMap<>();
    map.put(TYPE_HIERARCHY_TYPE, TypeHierarchyBrowserBase::getTypeHierarchyType);
    map.put(SUBTYPES_HIERARCHY_TYPE, TypeHierarchyBrowserBase::getSubtypesHierarchyType);
    map.put(SUPERTYPES_HIERARCHY_TYPE, TypeHierarchyBrowserBase::getSupertypesHierarchyType);
    return map;
  }

  public boolean isInterface() {
    return myIsInterface;
  }

  @Override
  protected void setHierarchyBase(@NotNull PsiElement element) {
    super.setHierarchyBase(element);
    myIsInterface = isInterface(element);
  }

  @Override
  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
    actionGroup.add(new ViewClassHierarchyAction());
    actionGroup.add(new ViewSupertypesHierarchyAction());
    actionGroup.add(new ViewSubtypesHierarchyAction());
    actionGroup.add(new AlphaSortAction());
  }

  @Override
  @NotNull
  protected String getActionPlace() {
    return ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR;
  }

  @Override
  public final Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myDeleteElementProvider;
    }
    return super.getData(dataId);
  }

  @Override
  @NotNull
  protected String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.type.prev.occurence.name");
  }

  @Override
  @NotNull
  protected String getNextOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.type.next.occurence.name");
  }

  private final class MyDeleteProvider implements DeleteProvider {
    @Override
    public final void deleteElement(@NotNull DataContext dataContext) {
      PsiElement aClass = getSelectedElement();
      if (!canBeDeleted(aClass)) return;
      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting.class", getQualifiedName(aClass)));
      try {
        PsiElement[] elements = {aClass};
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    @Override
    public final boolean canDeleteElement(@NotNull DataContext dataContext) {
      PsiElement aClass = getSelectedElement();
      if (!canBeDeleted(aClass)) {
        return false;
      }
      PsiElement[] elements = {aClass};
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }
  }


  protected static class BaseOnThisTypeAction extends BaseOnThisElementAction {

    public BaseOnThisTypeAction() {
      super(IdeBundle.messagePointer("action.base.on.this.class"), TypeHierarchyBrowserBase.class, LanguageTypeHierarchy.INSTANCE);
    }

    @Override
    protected String correctViewType(@NotNull HierarchyBrowserBaseEx browser, String viewType) {
      if (((TypeHierarchyBrowserBase)browser).myIsInterface && getTypeHierarchyType().equals(viewType)) {
        return getSubtypesHierarchyType();
      }
      return viewType;
    }
  }

  @SuppressWarnings("UnresolvedPropertyKey")
  public static String getTypeHierarchyType() {
    return IdeBundle.message("title.hierarchy.class");
  }

  @SuppressWarnings("UnresolvedPropertyKey")
  public static String getSubtypesHierarchyType() {
    return IdeBundle.message("title.hierarchy.subtypes");
  }

  @SuppressWarnings("UnresolvedPropertyKey")
  public static String getSupertypesHierarchyType() {
    return IdeBundle.message("title.hierarchy.supertypes");
  }
}
