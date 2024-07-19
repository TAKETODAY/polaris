/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.taketoday.polaris.jdbc.format;

import java.util.Locale;
import java.util.StringTokenizer;

import cn.taketoday.util.StringUtils;

/**
 * Performs formatting of DDL SQL statements.
 *
 * @author Gavin King
 * @author Steve Ebersole
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2022/9/12 19:20
 */
public class DDLSQLFormatter implements SQLFormatter {

  private static final String INITIAL_LINE = System.lineSeparator() + "    ";
  private static final String OTHER_LINES = System.lineSeparator() + "       ";

  /**
   * Singleton access
   */
  public static final DDLSQLFormatter INSTANCE = new DDLSQLFormatter();

  @Override
  public String format(String sql) {
    if (StringUtils.isEmpty(sql)) {
      return sql;
    }

    String lowerCase = sql.toLowerCase(Locale.ROOT);
    if (lowerCase.startsWith("create table")) {
      return formatCreateTable(sql);
    }
    else if (lowerCase.startsWith("create")) {
      return sql;
    }
    else if (lowerCase.startsWith("alter table")) {
      return formatAlterTable(sql);
    }
    else if (lowerCase.startsWith("comment on")) {
      return formatCommentOn(sql);
    }
    else {
      return INITIAL_LINE + sql;
    }
  }

  public static String formatCommentOn(String sql) {
    final StringBuilder result = new StringBuilder(60).append(INITIAL_LINE);
    final StringTokenizer tokens = new StringTokenizer(sql, " '[]\"", true);

    boolean quoted = false;
    while (tokens.hasMoreTokens()) {
      final String token = tokens.nextToken();
      result.append(token);
      if (isQuote(token)) {
        quoted = !quoted;
      }
      else if (!quoted) {
        if ("is".equals(token)) {
          result.append(OTHER_LINES);
        }
      }
    }

    return result.toString();
  }

  public static String formatAlterTable(String sql) {
    final StringBuilder result = new StringBuilder(60).append(INITIAL_LINE);
    final StringTokenizer tokens = new StringTokenizer(sql, " (,)'[]\"", true);

    boolean quoted = false;
    while (tokens.hasMoreTokens()) {
      final String token = tokens.nextToken();
      if (isQuote(token)) {
        quoted = !quoted;
      }
      else if (!quoted) {
        if (isBreak(token)) {
          result.append(OTHER_LINES);
        }
      }
      result.append(token);
    }

    return result.toString();
  }

  public static String formatCreateTable(String sql) {
    final StringBuilder result = new StringBuilder(60).append(INITIAL_LINE);
    final StringTokenizer tokens = new StringTokenizer(sql, "(,)'[]\"", true);

    int depth = 0;
    boolean quoted = false;
    while (tokens.hasMoreTokens()) {
      final String token = tokens.nextToken();
      if (isQuote(token)) {
        quoted = !quoted;
        result.append(token);
      }
      else if (quoted) {
        result.append(token);
      }
      else {
        if (")".equals(token)) {
          depth--;
          if (depth == 0) {
            result.append(INITIAL_LINE);
          }
        }
        result.append(token);
        if (",".equals(token) && depth == 1) {
          result.append(OTHER_LINES);
        }
        if ("(".equals(token)) {
          depth++;
          if (depth == 1) {
            result.append(OTHER_LINES);
          }
        }
      }
    }

    return result.toString();
  }

  private static boolean isBreak(String token) {
    return "drop".equals(token)
            || "add".equals(token)
            || "references".equals(token)
            || "foreign".equals(token)
            || "on".equals(token);
  }

  private static boolean isQuote(String tok) {
    return "\"".equals(tok)
            || "`".equals(tok)
            || "]".equals(tok)
            || "[".equals(tok)
            || "'".equals(tok);
  }

}
