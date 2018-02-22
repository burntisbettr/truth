/*
 * Copyright (c) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.truth;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.commonPrefix;
import static com.google.common.base.Strings.commonSuffix;
import static com.google.common.truth.Field.field;
import static com.google.common.truth.Field.makeMessage;
import static com.google.common.truth.Platform.ComparisonFailureMessageStrategy.OMIT_COMPARISON_FAILURE_GENERATED_MESSAGE;
import static java.lang.Character.isHighSurrogate;
import static java.lang.Character.isLowSurrogate;
import static java.lang.Math.max;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Platform.PlatformComparisonFailure;
import javax.annotation.Nullable;

/**
 * An {@link AssertionError} (usually a JUnit {@code ComparisonFailure}, but not under GWT) composed
 * of structured {@link Field} instances and other string messages.
 *
 * <p>This class includes logic to format expected and actual values for easier reading.
 */
final class ComparisonFailureWithFields extends PlatformComparisonFailure {
  static ComparisonFailureWithFields create(
      ImmutableList<String> messages,
      ImmutableList<Field> headFields,
      ImmutableList<Field> tailFields,
      String expected,
      String actual,
      @Nullable Throwable cause) {
    ImmutableList<Field> fields = makeFields(headFields, tailFields, expected, actual);
    return new ComparisonFailureWithFields(messages, fields, expected, actual, cause);
  }

  final ImmutableList<Field> fields;

  private ComparisonFailureWithFields(
      ImmutableList<String> messages,
      ImmutableList<Field> fields,
      String expected,
      String actual,
      @Nullable Throwable cause) {
    super(
        makeMessage(messages, fields),
        checkNotNull(expected),
        checkNotNull(actual),
        /* suffix= */ null,
        cause,
        OMIT_COMPARISON_FAILURE_GENERATED_MESSAGE);
    this.fields = checkNotNull(fields);
  }

  private static ImmutableList<Field> makeFields(
      ImmutableList<Field> headFields,
      ImmutableList<Field> tailFields,
      String expected,
      String actual) {
    return new ImmutableList.Builder<Field>()
        .addAll(headFields)
        .addAll(formatExpectedAndActual(expected, actual))
        .addAll(tailFields)
        .build();
  }

  /**
   * Returns one or more fields describing the difference between the given expected and actual
   * values.
   *
   * <p>Currently, this method always returns 2 fields, one each for expected and actual. Someday,
   * it may return a single diff-style field.
   *
   * <p>The 2 fields contain either the full expected and actual values or, if the values have a
   * long prefix or suffix in common, abbreviated values with "..." at the beginning or end.
   */
  @VisibleForTesting
  static ImmutableList<Field> formatExpectedAndActual(String expected, String actual) {
    ImmutableList<Field> result;

    result = removeCommonPrefixAndSuffix(expected, actual);
    if (result != null) {
      return result;
    }

    /*
     * TODO(cpovirk): Add other strategies, like diff-style output, possibly preferring it over
     * removeCommonPrefixAndSuffix when the values contain a newline.
     *
     * (For GWT, we probably won't offer diff-style: We'd need to make the diff library work there,
     * and even once we do, I think we'd lose the newlines. Hopefully no one under GWT has long,
     * nearly identical messages. In any case, they've always been stuck like this.
     */

    return ImmutableList.of(field("expected", expected), field("but was", actual));
  }

  @Nullable
  private static ImmutableList<Field> removeCommonPrefixAndSuffix(String expected, String actual) {
    // TODO(cpovirk): Use something like BreakIterator where available.
    int prefix = commonPrefix(expected, actual).length();
    prefix = max(0, prefix - CONTEXT);
    while (prefix > 0 && validSurrogatePairAt(expected, prefix - 1)) {
      prefix--;
    }
    if (prefix <= 3) {
      // If the common prefix is short, then we might as well just show it.
      prefix = 0;
    }

    int suffix = commonSuffix(expected, actual).length();
    suffix = max(0, suffix - CONTEXT);
    while (suffix > 0 && validSurrogatePairAt(expected, expected.length() - suffix - 1)) {
      suffix--;
    }
    if (suffix <= 3) {
      // If the common suffix is short, then we might as well just show it.
      suffix = 0;
    }

    if (prefix + suffix < WORTH_HIDING) {
      return null;
    }

    return ImmutableList.of(
        field("expected", hidePrefixAndSuffix(expected, prefix, suffix)),
        field("but was", hidePrefixAndSuffix(actual, prefix, suffix)));
  }

  private static final int CONTEXT = 5;
  private static final int WORTH_HIDING = 60;

  private static String hidePrefixAndSuffix(String s, int prefix, int suffix) {
    return maybeDots(prefix) + s.substring(prefix, s.length() - suffix) + maybeDots(suffix);
  }

  private static String maybeDots(int prefixOrSuffix) {
    return prefixOrSuffix > 0 ? "…" : "";
  }

  // From c.g.c.base.Strings.
  private static boolean validSurrogatePairAt(CharSequence string, int index) {
    return index >= 0
        && index <= (string.length() - 2)
        && isHighSurrogate(string.charAt(index))
        && isLowSurrogate(string.charAt(index + 1));
  }
}