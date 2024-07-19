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

package cn.taketoday.polaris.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import cn.taketoday.polaris.jdbc.parsing.ParameterIndexHolder;
import cn.taketoday.polaris.jdbc.parsing.QueryParameter;

/**
 * <pre>
 *     createNamedQuery("SELECT * FROM user WHERE id IN(:ids)")
 *      .addParameter("ids", 4, 5, 6)
 *      .fetch(...)
 * </pre> will generate the query :
 * <code>SELECT * FROM user WHERE id IN(4,5,6)</code><br>
 * <br>
 */
final class ArrayParameters {

  record ArrayParameter(int parameterIndex, int parameterCount) implements Comparable<ArrayParameter> {
    // parameterIndex the index of the parameter array
    // parameterCount the number of parameters to put in the query placeholder

    @Override
    public int compareTo(ArrayParameter o) {
      return Integer.compare(parameterIndex, o.parameterIndex);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof ArrayParameter that))
        return false;
      return parameterIndex == that.parameterIndex
              && parameterCount == that.parameterCount;
    }

    @Override
    public int hashCode() {
      return Objects.hash(parameterIndex, parameterCount);
    }
  }

  /**
   * Update both the query and the parameter indexes to include the array
   * parameters.
   *
   * @throws cn.taketoday.polaris.jdbc.ArrayParameterBindFailedException array parameter bind failed
   */
  static String updateQueryAndParametersIndexes(
          String parsedQuery,
          HashMap<String, cn.taketoday.polaris.jdbc.parsing.QueryParameter> queryParameters,
          boolean allowArrayParameters
  ) {
    ArrayList<ArrayParameter> arrayParameters
            = sortedArrayParameters(queryParameters, allowArrayParameters);
    if (arrayParameters.isEmpty()) {
      return parsedQuery;
    }

    updateMap(queryParameters, arrayParameters);
    return updateQueryWithArrayParameters(parsedQuery, arrayParameters);
  }

  /**
   * Update the indexes of each query parameter
   */
  static Map<String, cn.taketoday.polaris.jdbc.parsing.QueryParameter> updateMap(
          HashMap<String, cn.taketoday.polaris.jdbc.parsing.QueryParameter> queryParameters,
          ArrayList<ArrayParameter> arrayParametersSortedAsc
  ) {

    for (cn.taketoday.polaris.jdbc.parsing.QueryParameter parameter : queryParameters.values()) {
      ArrayList<Integer> newParameterIndex = new ArrayList<>();

      for (int parameterIndex : parameter.getHolder()) {
        int newIdx = computeNewIndex(parameterIndex, arrayParametersSortedAsc);
        newParameterIndex.add(newIdx);
      }

      if (newParameterIndex.size() > 1) {
        parameter.setHolder(cn.taketoday.polaris.jdbc.parsing.ParameterIndexHolder.valueOf(newParameterIndex));
      }
      else {
        parameter.setHolder(ParameterIndexHolder.valueOf(newParameterIndex.get(0)));
      }
    }

    return queryParameters;
  }

  /**
   * Compute the new index of a parameter given the index positions of the array
   * parameters.
   */
  static int computeNewIndex(int index, ArrayList<ArrayParameter> arrayParametersSortedAsc) {
    int newIndex = index;
    for (ArrayParameter arrayParameter : arrayParametersSortedAsc) {
      if (index > arrayParameter.parameterIndex) {
        newIndex = newIndex + arrayParameter.parameterCount - 1;
      }
      else {
        return newIndex;
      }
    }
    return newIndex;
  }

  /**
   * List all the array parameters that contains more that 1 parameters. Indeed,
   * array parameter below 1 parameter will not change the text query nor the
   * parameter indexes.
   *
   * @throws cn.taketoday.polaris.jdbc.ArrayParameterBindFailedException array parameter bind failed
   */
  private static ArrayList<ArrayParameter> sortedArrayParameters(
          HashMap<String, cn.taketoday.polaris.jdbc.parsing.QueryParameter> queryParameters,
          boolean allowArrayParameters
  ) {
    ArrayList<ArrayParameter> arrayParameters = new ArrayList<>();
    for (QueryParameter parameter : queryParameters.values()) {
      ParameterBinder binder = parameter.getBinder();
      if (binder instanceof NamedQuery.ArrayParameterBinder) {
        int parameterCount = ((NamedQuery.ArrayParameterBinder) binder).getParameterCount();
        if (parameterCount > 1) {
          if (!allowArrayParameters) {
            throw new ArrayParameterBindFailedException("Array parameters are not allowed in batch mode");
          }

          for (int index : parameter.getHolder()) {
            arrayParameters.add(new ArrayParameter(index, parameterCount));
          }
        }
      }
    }
    if (arrayParameters.size() > 1) {
      Collections.sort(arrayParameters);
    }
    return arrayParameters;
  }

  /**
   * Change the query to replace ? at each arrayParametersSortedAsc.parameterIndex
   * with ?,?,?.. multiple arrayParametersSortedAsc.parameterCount
   */
  static String updateQueryWithArrayParameters(
          String parsedQuery, ArrayList<ArrayParameter> arrayParametersSortedAsc
  ) {
    if (arrayParametersSortedAsc.isEmpty()) {
      return parsedQuery;
    }

    StringBuilder sb = new StringBuilder();

    Iterator<ArrayParameter> parameterToReplaceIt = arrayParametersSortedAsc.iterator();
    ArrayParameter nextParameterToReplace = parameterToReplaceIt.next();
    // PreparedStatement index starts at 1
    int currentIndex = 1;
    for (char c : parsedQuery.toCharArray()) {
      if (nextParameterToReplace != null && c == '?') {
        if (currentIndex == nextParameterToReplace.parameterIndex) {
          sb.append('?');
          for (int i = 1; i < nextParameterToReplace.parameterCount; i++) {
            sb.append(",?");
          }

          if (parameterToReplaceIt.hasNext()) {
            nextParameterToReplace = parameterToReplaceIt.next();
          }
          else {
            nextParameterToReplace = null;
          }
        }
        else {
          sb.append(c);
        }
        currentIndex++;
      }
      else {
        sb.append(c);
      }
    }

    return sb.toString();
  }

}
