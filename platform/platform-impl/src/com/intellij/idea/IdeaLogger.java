// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.LogMessage;
import com.intellij.diagnostic.VMOptions;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.ide.plugins.PluginUtilImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class IdeaLogger extends Log4jBasedLogger {
  @SuppressWarnings("StaticNonFinalField") public static String ourLastActionId = "";
  // when not null, holds the first of errors that occurred
  @SuppressWarnings("StaticNonFinalField") public static Exception ourErrorsOccurred;

  /**
   * We try to report exceptions thrown from frequently called methods (e.g. {@link Component#paint(Graphics)}) judiciously,
   * so that instead of polluting the log with hundreds of identical {@link com.intellij.openapi.diagnostic.Logger#error(Throwable) LOG.errors}
   * we print the error message and the stacktrace once in a while.
   */
  final int REPORT_EVERY_NTH_FREQUENT_EXCEPTION = Integer.getInteger("ide.muted.error.logger.frequency", 10);
  private static final int EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES = Integer.getInteger("ide.muted.error.logger.expiration", 5);

  // must be as a separate class to avoid initialization as part of start-up (file logger configuration)
  private static final class MyCache {
    private static final Cache<@NotNull String, @NotNull AtomicInteger> cache = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(Math.max(EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES, 0), TimeUnit.MINUTES)
      .build();

    @NotNull
    private static AtomicInteger getOrCreate(int hash, @NotNull Throwable t) {
      return cache.get(hash+":"+t, __ -> new AtomicInteger());
    }
  }

  public static void dropFrequentExceptionsCaches() {
    MyCache.cache.invalidateAll();
    MyCache.cache.cleanUp();
  }

  private boolean isTooFrequentException(@Nullable Throwable t) {
    if (t == null || !isMutingFrequentExceptionsEnabled() || !LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return false;
    }

    int hash = ThrowableInterner.computeAccurateTraceHashCode(t);
    AtomicInteger counter = MyCache.getOrCreate(hash, t);
    int occurrences = counter.incrementAndGet();
    if (occurrences == 1) {
      return false;
    }

    reportToFus(t);

    if (occurrences % REPORT_EVERY_NTH_FREQUENT_EXCEPTION == 0 && occurrences > 1) {
      error(false, getExceptionWasAlreadyReportedNTimesMessage(t, occurrences), null);
    }

    return true;
  }

  @NotNull
  static String getExceptionWasAlreadyReportedNTimesMessage(@NotNull Throwable t, int occurrences) {
    return "Exception '" + t + "' was reported " + occurrences + " times";
  }

  private static void reportToFus(@NotNull Throwable t) {
    Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode() && !application.isDisposed()) {
      PluginId pluginId = PluginUtil.getInstance().findPluginId(t);
      VMOptions.MemoryKind kind = DefaultIdeaErrorLogger.getOOMErrorKind(t);
      LifecycleUsageTriggerCollector.onError(pluginId, t, kind);
    }
  }

  static boolean isMutingFrequentExceptionsEnabled() {
    return EXPIRE_FREQUENT_EXCEPTIONS_AFTER_MINUTES > 0;
  }

  private static final Supplier<String> ourApplicationInfoProvider = () -> {
    ApplicationInfoEx info = ApplicationInfoImpl.getShadowInstance();
    return info.getFullApplicationName() + "  " + "Build #" + info.getBuild().asString();
  };

  private static final ThrowableRenderer ourThrowableRenderer = t -> {
    String[] lines = DefaultThrowableRenderer.render(t);
    int maxStackSize = 1024;
    int maxExtraSize = 256;
    if (lines.length > maxStackSize + maxExtraSize) {
      String[] res = new String[maxStackSize + maxExtraSize + 1];
      System.arraycopy(lines, 0, res, 0, maxStackSize);
      res[maxStackSize] = "\t...";
      System.arraycopy(lines, lines.length - maxExtraSize, res, maxStackSize + 1, maxExtraSize);
      return res;
    }
    return lines;
  };

  public static @NotNull ThrowableRenderer getThrowableRenderer() {
    return ourThrowableRenderer;
  }

  IdeaLogger(@NotNull Logger logger) {
    super(logger);
    LoggerRepository repository = myLogger.getLoggerRepository();
    if (repository instanceof ThrowableRendererSupport) {
      ((ThrowableRendererSupport)repository).setThrowableRenderer(ourThrowableRenderer);
    }
  }

  @Override
  public void error(Object message) {
    if (message instanceof IdeaLoggingEvent) {
      myLogger.error(message);
    }
    else {
      super.error(message);
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    if (isTooFrequentException(t)) return;
    myLogger.error(LogMessage.createEvent(t != null ? t : new Throwable(), message, attachments));
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (isTooFrequentException(t)) return;
    super.warn(message, ensureNotControlFlow(t));
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (isTooFrequentException(t)) return;
    error(true, message, t, details);
  }

  private void error(boolean addErrorHeader, String message, @Nullable Throwable t, String @NotNull ... details) {
    if (t instanceof ControlFlowException) {
      myLogger.error(message, ensureNotControlFlow(t));
      ExceptionUtil.rethrow(t);
    }

    String detailString = String.join("\n", details);
    if (!detailString.isEmpty()) {
      detailString = "\nDetails: " + detailString;
    }

    if (ourErrorsOccurred == null) {
      String mess = "Logger errors occurred. See IDEA logs for details. " +
                    (message == null || message.isEmpty() ? "" : "Error message is '" + message + "'");
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourErrorsOccurred = new Exception(mess + detailString, t);
    }
    myLogger.error(message + detailString, t);
    if (addErrorHeader) {
      logErrorHeader(t);
    }
  }

  private void logErrorHeader(@Nullable Throwable t) {
    myLogger.error(ourApplicationInfoProvider.get());

    Properties properties = System.getProperties();
    myLogger.error("JDK: " + properties.getProperty("java.version", "unknown") +
                   "; VM: " + properties.getProperty("java.vm.name", "unknown") +
                   "; Vendor: " + properties.getProperty("java.vendor", "unknown"));
    myLogger.error("OS: " + properties.getProperty("os.name", "unknown"));

    // do not use getInstance here - container maybe already disposed
    if (t != null && PluginManagerCore.arePluginsInitialized()) {
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginUtilImpl.doFindPluginId(t));
      if (plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate())) {
        myLogger.error("Plugin to blame: " + plugin.getName() + " version: " + plugin.getVersion());
      }
    }

    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null && application.isComponentCreated() && !application.isDisposed()) {
      String lastPreformedActionId = ourLastActionId;
      if (lastPreformedActionId != null) {
        myLogger.error("Last Action: " + lastPreformedActionId);
      }

      CommandProcessor commandProcessor = application.getServiceIfCreated(CommandProcessor.class);
      if (commandProcessor != null) {
        String currentCommandName = commandProcessor.getCurrentCommandName();
        if (currentCommandName != null) {
          myLogger.error("Current Command: " + currentCommandName);
        }
      }
    }
  }
}