// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static git4idea.config.GitExecutableManager.runUnderProgressIfNeeded;

/**
 * Tries to detect the path to Git executable.
 */
public class GitExecutableDetector {

  private static final Logger LOG = Logger.getInstance(GitExecutableDetector.class);
  private static final @NonNls String[] UNIX_PATHS = {
    "/usr/local/bin",
    "/opt/local/bin",
    "/usr/bin",
    "/opt/bin",
    "/usr/local/git/bin"};

  private static final @NonNls String GIT = "git";
  private static final @NonNls String UNIX_EXECUTABLE = GIT;

  private static final File WIN_ROOT = new File("C:\\"); // the constant is extracted to be able to create files in "Program Files" in tests
  private static final @NonNls String GIT_EXE = "git.exe";

  private static final String WIN_EXECUTABLE = GIT_EXE;

  @NotNull private final Object DETECTED_EXECUTABLE_LOCK = new Object();
  @NotNull private final Map<WSLDistribution, String> myWslExecutables = new ConcurrentHashMap<>(); // concurrent to read without lock
  @Nullable private volatile String myDetectedExecutable;
  private boolean myDetectionComplete;

  @Nullable
  public String detect(@Nullable WSLDistribution distribution) {
    return runUnderProgressIfNeeded(null, GitBundle.message("git.executable.detect.progress.title"), () -> {
      synchronized (DETECTED_EXECUTABLE_LOCK) {
        if (!myDetectionComplete) {
          myDetectedExecutable = runDetect();
          myDetectionComplete = true;
        }
        return getExecutable(distribution);
      }
    });
  }

  public void clear() {
    synchronized (DETECTED_EXECUTABLE_LOCK) {
      myWslExecutables.clear();
      myDetectedExecutable = null;
      myDetectionComplete = false;
    }
  }

  @Nullable
  public String getExecutable(@Nullable WSLDistribution projectWslDistribution) {
    if (projectWslDistribution != null) {
      String exec = myWslExecutables.get(projectWslDistribution);
      if (exec != null) return exec;
    }

    return myDetectedExecutable;
  }

  @Nullable
  private String runDetect() {
    detectAvailableWsl();

    File gitExecutableFromPath = PathEnvironmentVariableUtil.findInPath(SystemInfo.isWindows ? GIT_EXE : GIT, getPath(), null);
    if (gitExecutableFromPath != null) return gitExecutableFromPath.getAbsolutePath();

    return SystemInfo.isWindows ? detectForWindows() : detectForUnix();
  }

  @NotNull
  public static String getDefaultExecutable() {
    return SystemInfo.isWindows ? WIN_EXECUTABLE : UNIX_EXECUTABLE;
  }

  @Nullable
  private static String detectForUnix() {
    for (String p : UNIX_PATHS) {
      File f = new File(p, UNIX_EXECUTABLE);
      if (f.exists()) {
        return f.getPath();
      }
    }
    return null;
  }

  @Nullable
  private String detectForWindows() {
    String exec = checkProgramFiles();
    if (exec != null) {
      return exec;
    }

    exec = checkCygwin();
    if (exec != null) {
      return exec;
    }

    exec = checkWsl();
    if (exec != null) {
      return exec;
    }

    return null;
  }

  @Nullable
  private String checkProgramFiles() {
    final String[] PROGRAM_FILES = {"Program Files", "Program Files (x86)"};

    // collecting all potential msys distributives
    List<File> distrs = new ArrayList<>();
    for (String programFiles : PROGRAM_FILES) {
      File pf = new File(getWinRoot(), programFiles);
      File[] children = pf.listFiles(pathname -> pathname.isDirectory() && StringUtil.toLowerCase(pathname.getName()).startsWith("git"));
      if (!pf.exists() || children == null) {
        continue;
      }
      distrs.addAll(Arrays.asList(children));
    }

    // greater is better => sorting in the descending order to match the best version first, when iterating
    distrs.sort(Collections.reverseOrder(new VersionDirsComparator()));

    for (File distr : distrs) {
      String exec = checkDistributive(distr);
      if (exec != null) {
        return exec;
      }
    }
    return null;
  }

  @Nullable
  private String checkCygwin() {
    final String[] OTHER_WINDOWS_PATHS = {FileUtil.toSystemDependentName("cygwin/bin/git.exe")};
    for (String otherPath : OTHER_WINDOWS_PATHS) {
      File file = new File(getWinRoot(), otherPath);
      if (file.exists()) {
        return file.getPath();
      }
    }
    return null;
  }

  @Nullable
  private String checkWsl() {
    if (myWslExecutables.size() == 1) {
      return myWslExecutables.values().iterator().next();
    }
    return null;
  }

  private void detectAvailableWsl() {
    if (!GitExecutableManager.supportWslExecutable()) return;

    List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributions();
    for (WSLDistribution distribution : distributions) {
      String path = checkWslDistribution(distribution);
      if (path != null) myWslExecutables.put(distribution, path);
    }
  }

  @Nullable
  private static String checkWslDistribution(@NotNull WSLDistribution distribution) {
    if (WSLUtil.isWsl1(distribution) != ThreeState.NO) return null;

    File root = distribution.getUNCRoot();
    for (String p : UNIX_PATHS) {
      File d = new File(root, p);
      File f = new File(d, UNIX_EXECUTABLE);
      if (f.exists()) {
        return f.getPath();
      }
    }
    return null;
  }

  @VisibleForTesting
  @NotNull
  protected File getWinRoot() {
    return WIN_ROOT;
  }

  @Nullable
  private static String checkDistributive(@Nullable File gitDir) {
    if (gitDir == null || !gitDir.exists()) {
      return null;
    }

    final String[] binDirs = {"cmd", "bin"};
    for (String binDir : binDirs) {
      String exec = checkBinDir(new File(gitDir, binDir));
      if (exec != null) {
        return exec;
      }
    }

    return null;
  }

  @Nullable
  private static String checkBinDir(@NotNull File binDir) {
    if (!binDir.exists()) {
      return null;
    }

    File fe = new File(binDir, GIT_EXE);
    if (fe.exists()) {
      return fe.getPath();
    }

    return null;
  }

  @VisibleForTesting
  @Nullable
  protected String getPath() {
    return PathEnvironmentVariableUtil.getPathVariableValue();
  }

  @Nullable
  public static String patchExecutablePath(@NotNull String path) {
    if (SystemInfo.isWindows) {
      File file = new File(path);
      if (file.getName().equals("git-cmd.exe") || file.getName().equals("git-bash.exe")) {
        File patchedFile = new File(file.getParent(), "bin/git.exe");
        if (patchedFile.exists()) return patchedFile.getPath();
      }
    }
    return null;
  }

  // Compare strategy: greater is better (if v1 > v2, then v1 is a better candidate for the Git executable)
  private static class VersionDirsComparator implements Comparator<File> {

    @Override
    public int compare(File f1, File f2) {
      String name1 = StringUtil.toLowerCase(f1.getName());
      String name2 = StringUtil.toLowerCase(f2.getName());

      // C:\Program Files\Git is better candidate for _default_ than C:\Program Files\Git_1.8.0
      if (name1.equals("git")) {
        return name2.equals("git") ? fallback(f1, f2) : 1;
      }
      else if (name2.equals("git")) {
        return -1;
      }

      final Pattern GIT_WITH_VERSION = Pattern.compile("^git[ _]*([\\d\\.]*).*$");
      Matcher m1 = GIT_WITH_VERSION.matcher(name1);
      Matcher m2 = GIT_WITH_VERSION.matcher(name2);
      if (m1.matches() && m2.matches()) {
        GitVersion v1 = parseGitVersion(m1.group(1));
        GitVersion v2 = parseGitVersion(m2.group(1));
        if (v1 == null || v2 == null) {
          return fallback(f1, f2);
        }
        int compareVersions = v1.compareTo(v2);
        return compareVersions == 0 ? fallback(f1, f2) : compareVersions;
      }
      return fallback(f1, f2);
    }

    private static int fallback(@NotNull File f1, @NotNull File f2) {
      // "Program Files" is preferable over "Program Files (x86)"
      int compareParents = f1.getParentFile().getName().compareTo(f2.getParentFile().getName());
      if (compareParents != 0) {
        return -compareParents; // greater is better => reversing
      }

      // probably some unrecognized format of Git directory naming => just compare lexicographically
      String name1 = StringUtil.toLowerCase(f1.getName());
      String name2 = StringUtil.toLowerCase(f2.getName());
      return name1.compareTo(name2);
    }

    // not using GitVersion#parse(), because it requires at least 3 items in the version (1.7.3),
    // and parses the `git version` command output, not just the version string.
    @Nullable
    private static GitVersion parseGitVersion(@Nullable String name) {
      if (name == null) {
        return null;
      }
      final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?.*");
      Matcher m = VERSION.matcher(name);
      if (!m.matches()) {
        return null;
      }
      try {
        int major = Integer.parseInt(m.group(1));
        return new GitVersion(major, parseOrNull(m.group(2)), parseOrNull(m.group(3)), parseOrNull(m.group(4)));
      }
      catch (NumberFormatException e) {
        LOG.info("Unexpected NFE when parsing [" + name + "]", e);
        return null;
      }
    }

    private static int parseOrNull(String group) {
      return group == null ? 0 : Integer.parseInt(group);
    }
  }
}
