// Copyright (c) YugaByte, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied.  See the License for the specific language governing permissions and limitations
// under the License.
//

package org.yb.pgsql;

import static org.yb.AssertionWrappers.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.yb.util.ProcessUtil;
import org.yb.util.SideBySideDiff;
import org.yb.util.StringUtil;
import org.yb.util.YBTestRunnerNonTsanAsan;

import com.google.common.collect.Sets;

@RunWith(value=YBTestRunnerNonTsanAsan.class)
public class TestYsqlDump extends BasePgSQLTest {
  private static final Logger LOG = LoggerFactory.getLogger(TestYsqlDump.class);

  @Override
  public int getTestMethodTimeoutSec() {
    return super.getTestMethodTimeoutSec() * 10;
  }

  @Override
  protected Map<String, String> getMasterFlags() {
    Map<String, String> flagMap = super.getMasterFlags();
    flagMap.put("TEST_sequential_colocation_ids", "true");
    return flagMap;
  }

  @Override
  protected Map<String, String> getTServerFlags() {
    Map<String, String> flagMap = super.getTServerFlags();
    // Turn off sequence cache.
    flagMap.put("ysql_sequence_cache_minval", "0");
    return flagMap;
  }

  // The following logic is needed to remove the dependency on the exact version number from
  // the ysql_dump output part that looks like this:
  // -- Dumped from database version 11.2-YB-1.3.2.0-b0
  // -- Dumped by ysql_dump version 11.2-YB-1.3.2.0-b0

  private static Pattern VERSION_NUMBER_PATTERN = Pattern.compile(
      " version [0-9]+[.][0-9]+-YB-([0-9]+[.]){3}[0-9]+-b[0-9]+");

  private static String  VERSION_NUMBER_REPLACEMENT_STR =
      " version X.X-YB-X.X.X.X-bX";

  private String postprocessOutputLine(String s) {
    if (s == null)
      return null;
    return StringUtil.expandTabsAndRemoveTrailingSpaces(
      VERSION_NUMBER_PATTERN.matcher(s).replaceAll(VERSION_NUMBER_REPLACEMENT_STR));
  }

  @Test
  public void ysqlDump() throws Exception {
    ysqlDumpTester(
        "ysql_dump" /* binaryName */,
        "sql/yb_ysql_dump.sql" /* inputFileRelativePath */,
        "sql/yb_ysql_dump_describe.sql" /* inputDescribeFileRelativePath */,
        "data/yb_ysql_dump.data.sql" /* expectedDumpRelativePath */,
        "expected/yb_ysql_dump_describe.out" /* expectedDescribeFileRelativePath */,
        "results/yb_ysql_dump.out" /* outputFileRelativePath */,
        "results/yb_ysql_dump_describe.out" /* outputDescribeFileRelativePath */);
  }

  @Test
  public void ysqlDumpAll() throws Exception {
    // Note that we're using the same describe input as for regular ysql_dump!
    ysqlDumpTester(
        "ysql_dumpall" /* binaryName */,
        "sql/yb_ysql_dumpall.sql" /* inputFileRelativePath */,
        "sql/yb_ysql_dump_describe.sql" /* inputDescribeFileRelativePath */,
        "data/yb_ysql_dumpall.data.sql" /* expectedDumpRelativePath */,
        "expected/yb_ysql_dumpall_describe.out" /* expectedDescribeFileRelativePath */,
        "results/yb_ysql_dumpall.out" /* outputFileRelativePath */,
        "results/yb_ysql_dumpall_describe.out" /* outputDescribeFileRelativePath */);
  }

  void ysqlDumpTester(final String binaryName,
                      final String inputFileRelativePath,
                      final String inputDescribeFileRelativePath,
                      final String expectedDumpRelativePath,
                      final String expectedDescribeFileRelativePath,
                      final String outputFileRelativePath,
                      final String outputDescribeFileRelativePath) throws Exception {
    // Location of Postgres regression tests
    File pgRegressDir = PgRegressBuilder.PG_REGRESS_DIR;

    // Create the data
    List<String> inputLines =
        FileUtils.readLines(new File(pgRegressDir, inputFileRelativePath),
                            StandardCharsets.UTF_8);
    try (Statement statement = connection.createStatement()) {
      for (String inputLine : inputLines) {
        statement.execute(inputLine);
      }
    }

    // Dump and validate the data
    File pgBinDir     = PgRegressBuilder.getPgBinDir();
    File ysqlDumpExec = new File(pgBinDir, binaryName);

    File expected = new File(pgRegressDir, expectedDumpRelativePath);
    File actual   = new File(pgRegressDir, outputFileRelativePath);
    actual.getParentFile().mkdirs();

    int tserverIndex = 0;

    ProcessUtil.executeSimple(Arrays.asList(
      ysqlDumpExec.toString(),
      "-h", getPgHost(tserverIndex),
      "-p", Integer.toString(getPgPort(tserverIndex)),
      "-U", DEFAULT_PG_USER,
      "-f", actual.toString(),
      "--include-yb-metadata"
    ), binaryName);

    assertOutputFile(expected, actual);

    File ysqlshExec = new File(pgBinDir, "ysqlsh");

    File inputDesc    = new File(pgRegressDir, inputDescribeFileRelativePath);
    File expectedDesc = new File(pgRegressDir, expectedDescribeFileRelativePath);
    File actualDesc   = new File(pgRegressDir, outputDescribeFileRelativePath);
    actualDesc.getParentFile().mkdirs();

    // Run some validations
    ProcessUtil.executeSimple(Arrays.asList(
      ysqlshExec.toString(),
      "-h", getPgHost(tserverIndex),
      "-p", Integer.toString(getPgPort(tserverIndex)),
      "-U", DEFAULT_PG_USER,
      "-f", inputDesc.toString(),
      "-o", actualDesc.toString()
    ), "ysqlsh (validate describes)");

    assertOutputFile(expectedDesc, actualDesc);
  }

  /** Compare the expected output and the actual output. */
  private void assertOutputFile(File expected, File actual) throws IOException {
    List<String> expectedLines = FileUtils.readLines(expected, StandardCharsets.UTF_8);
    List<String> actualLines   = FileUtils.readLines(actual, StandardCharsets.UTF_8);

    // Create the side-by-side diff between the actual output and expected output.
    // The resulting string will be used to provide debug information if the below
    // comparison between the two files fails.
    String message = "Side-by-side diff between expected output and actual output:\n" +
          new SideBySideDiff(expected, actual).getSideBySideDiff();

    int i = 0;
    for (; i < expectedLines.size() && i < actualLines.size(); ++i) {
      assertEquals(message,
                   postprocessOutputLine(expectedLines.get(i)),
                   postprocessOutputLine(actualLines.get(i)));
    }
    assertOnlyEmptyLines(message, expectedLines.subList(i, expectedLines.size()));
    assertOnlyEmptyLines(message, actualLines.subList(i, actualLines.size()));
  }

  private void assertOnlyEmptyLines(String message, List<String> lines) {
    Set<String> processedLinesSet =
        lines.stream().map((l) -> l.trim()).collect(Collectors.toSet());
    assertTrue(message, Sets.newHashSet("").containsAll(processedLinesSet));
  }
}
