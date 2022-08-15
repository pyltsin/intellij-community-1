// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.psi.PsiMethod;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public final class ConsumedStreamUtils {

  public static final CallMatcher INTERMEDIATE_OPERATION_MATCHERS = anyOf(
    instanceCall(JAVA_UTIL_STREAM_STREAM, "filter", "map", "mapToInt", "mapToLong", "mapToDouble", "flatMap", "flatMapToInt",
                 "flatMapToLong", "flatMapToDouble", "mapMulti", "mapMultiToInt", "mapMultiToLong", "mapMultiToDouble", "distinct",
                 "sorted", "peek", "limit", "skip", "takeWhile", "dropWhile"),
    instanceCall(JAVA_UTIL_STREAM_INT_STREAM, "filter", "map", "mapToObj", "mapToLong", "mapToDouble", "flatMap", "distinct",
                 "sorted", "peek", "limit", "skip", "takeWhile", "dropWhile", "asLongStream", "asDoubleStream", "boxed", "mapMulti"),
    instanceCall(JAVA_UTIL_STREAM_LONG_STREAM, "filter", "map", "mapToObj", "mapToInt", "mapToDouble", "flatMap", "distinct",
                 "sorted", "peek", "limit", "skip", "takeWhile", "dropWhile",  "asDoubleStream", "boxed", "mapMulti"),
    instanceCall(JAVA_UTIL_STREAM_DOUBLE_STREAM, "filter", "map", "mapToObj", "mapToInt", "mapToLong", "flatMap", "distinct",
                 "sorted", "peek", "limit", "skip", "takeWhile", "dropWhile", "boxed", "mapMulti"));

  private static final CallMatcher ADDITIONAL_CHECKED_STREAM_MATCHERS = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "onClose");

  private static final CallMatcher ADDITIONAL_MARK_STREAM_MATCHERS = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "close");

  private static final CallMatcher SKIP_STREAM_MATCHERS = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "unordered", "parallel", "sequential");
  private static final CallMatcher MARK_AND_CONSUMED_STREAM_MATCHERS = anyOf(
    INTERMEDIATE_OPERATION_MATCHERS,
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "iterator", "spliterator"),
    instanceCall(JAVA_UTIL_STREAM_STREAM, "forEach", "forEachOrdered", "toArray", "reduce", "collect", "min", "max", "count", "anyMatch",
                 "allMatch", "nonMatch", "findFirst", "findAny", "toList"),
    instanceCall(JAVA_UTIL_STREAM_INT_STREAM, "forEach", "forEachOrdered", "toArray", "reduce", "collect",
                 "sum", "min", "max", "count", "average", "summaryStatistics", "anyMatch", "allMatch", "noneMatch", "findFirst", "findAny"),
    instanceCall(JAVA_UTIL_STREAM_LONG_STREAM, "forEach", "forEachOrdered", "toArray", "reduce", "collect",
                 "sum", "min", "max", "count", "average", "summaryStatistics", "anyMatch", "allMatch", "noneMatch", "findFirst", "findAny"),
    instanceCall(JAVA_UTIL_STREAM_DOUBLE_STREAM,  "forEach", "forEachOrdered", "toArray", "reduce", "collect",
                 "sum", "min", "max", "count", "average", "summaryStatistics", "anyMatch", "allMatch", "noneMatch", "findFirst", "findAny")
  );
  public static final CallMatcher CALL_MATCHERS_FOR_MARK_CONSUMED =
    anyOf(MARK_AND_CONSUMED_STREAM_MATCHERS, ADDITIONAL_MARK_STREAM_MATCHERS);
  public static final CallMatcher NONE_LEAKS_STREAM_MATCHERS =
    anyOf(MARK_AND_CONSUMED_STREAM_MATCHERS, ADDITIONAL_MARK_STREAM_MATCHERS, ADDITIONAL_CHECKED_STREAM_MATCHERS, SKIP_STREAM_MATCHERS);

  private static final CallMatcher STREAM_GENERATORS = anyOf(
    staticCall(JAVA_UTIL_STREAM_STREAM, "empty", "of", "ofNullable", "iterate", "generate", "concat"),
    staticCall(JAVA_UTIL_STREAM_INT_STREAM, "empty", "of", "iterate", "generate", "range", "rangeClosed", "concat"),
    staticCall(JAVA_UTIL_STREAM_LONG_STREAM, "empty", "of", "iterate", "generate", "range", "rangeClosed", "concat"),
    staticCall(JAVA_UTIL_STREAM_DOUBLE_STREAM, "empty", "of", "iterate", "generate", "concat"),
    staticCall("java.util.stream.StreamSupport", "stream", "intStream", "longStream", "doubleStream"),
    instanceCall(JAVA_UTIL_COLLECTION, "stream", "parallelStream"),
    staticCall(JAVA_UTIL_ARRAYS, "stream"),
    INTERMEDIATE_OPERATION_MATCHERS);
  private static final CallMapper<Boolean> CHECKED_CALL_MATCHERS = new CallMapper<Boolean>()
    .register(MARK_AND_CONSUMED_STREAM_MATCHERS, Boolean.TRUE)
    .register(ADDITIONAL_CHECKED_STREAM_MATCHERS, Boolean.TRUE);

  public static boolean isCheckedCallForConsumedStream(PsiMethod method) {
    return CHECKED_CALL_MATCHERS.mapFirst(method) == Boolean.TRUE;
  }

  public static CallMatcher getSkipMatchers() {
    return SKIP_STREAM_MATCHERS;
  }

  public static CallMatcher getCallMatchersForMarkConsumed() {
    return CALL_MATCHERS_FOR_MARK_CONSUMED;
  }

  public static CallMatcher getAllNonLeakStreamMatchers() {
    return NONE_LEAKS_STREAM_MATCHERS;
  }

  public static CallMatcher getStreamGenerators() {
    return STREAM_GENERATORS;
  }
}
