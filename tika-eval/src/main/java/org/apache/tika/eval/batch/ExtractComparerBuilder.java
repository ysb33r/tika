/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.eval.batch;


import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.ExtractComparer;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.ExtractReader;
import org.apache.tika.util.PropsUtil;

public class ExtractComparerBuilder extends EvalConsumerBuilder {
    private final List<TableInfo> tableInfosA;
    private final List<TableInfo> tableInfosB;
    private final List<TableInfo> tableInfosAandB;
    private final List<TableInfo> refTableInfos;

    public ExtractComparerBuilder() {
        List<TableInfo> tableInfosA = new ArrayList<>();
        List<TableInfo> tableInfosB = new ArrayList<>();
        List<TableInfo> tableInfosAandB = new ArrayList<>();
        tableInfosA.add(ExtractComparer.PROFILES_A);
        tableInfosA.add(ExtractComparer.EXTRACT_EXCEPTION_TABLE_A);
        tableInfosA.add(ExtractComparer.EXCEPTION_TABLE_A);
        tableInfosA.add(ExtractComparer.CONTENTS_TABLE_A);
        tableInfosA.add(ExtractComparer.EXTRACT_EXCEPTION_TABLE_A);
        tableInfosA.add(ExtractComparer.EMBEDDED_FILE_PATH_TABLE_A);

        tableInfosB.add(ExtractComparer.PROFILES_B);
        tableInfosB.add(ExtractComparer.EXTRACT_EXCEPTION_TABLE_B);
        tableInfosB.add(ExtractComparer.EXCEPTION_TABLE_B);
        tableInfosB.add(ExtractComparer.EXTRACT_EXCEPTION_TABLE_B);
        tableInfosB.add(ExtractComparer.CONTENTS_TABLE_B);
        tableInfosB.add(ExtractComparer.EMBEDDED_FILE_PATH_TABLE_B);

        tableInfosAandB.add(ExtractComparer.COMPARISON_CONTAINERS);
        tableInfosAandB.add(ExtractComparer.CONTENT_COMPARISONS);
        tableInfosAandB.add(AbstractProfiler.MIME_TABLE);

        List<TableInfo> refTableInfos = new ArrayList<>();
        refTableInfos.add(ExtractComparer.REF_PAIR_NAMES);
        refTableInfos.add(AbstractProfiler.REF_PARSE_ERROR_TYPES);
        refTableInfos.add(AbstractProfiler.REF_PARSE_EXCEPTION_TYPES);
        refTableInfos.add(AbstractProfiler.REF_EXTRACT_EXCEPTION_TYPES);

        this.tableInfosA = Collections.unmodifiableList(tableInfosA);
        this.tableInfosB = Collections.unmodifiableList(tableInfosB);
        this.tableInfosAandB = Collections.unmodifiableList(tableInfosAandB);
        this.refTableInfos = Collections.unmodifiableList(refTableInfos);
    }

    @Override
    public FileResourceConsumer build() throws IOException, SQLException {
        Path extractsA = PropsUtil.getPath(localAttrs.get("extractsA"), null);
        if (extractsA == null) {
            throw new RuntimeException("Must specify \"extractsA\" -- directory for 'A' extracts");
        }
        Path extractsB = PropsUtil.getPath(localAttrs.get("extractsB"), null);
        if (extractsB == null) {
            throw new RuntimeException("Must specify \"extractsB\" -- directory for 'B' extracts");
        }

        Path inputRootDir = PropsUtil.getPath(localAttrs.get("inputDir"), null);

        long minExtractLength = PropsUtil.getLong(localAttrs.get("minExtractLength"), -1L);
        long maxExtractLength = PropsUtil.getLong(localAttrs.get("maxExtractLength"), -1L);

        ExtractReader.ALTER_METADATA_LIST alterExtractList = getAlterMetadata(localAttrs);


        if (inputRootDir == null) {
            //this is for the sake of the crawler
            throw new RuntimeException("Must specify an -inputDir");
        }

        return new ExtractComparer(queue, inputRootDir, extractsA, extractsB,
                new ExtractReader(alterExtractList, minExtractLength, maxExtractLength),
                getDBWriter());
    }

    @Override
    protected List<TableInfo> getTableInfos(String tableNamePrefixA, String tableNamePrefixB) {
        List<TableInfo> allTables = new ArrayList<>();
        if (tableNamePrefixA != null) {
            for (TableInfo tableInfo : tableInfosA) {
                tableInfo.setNamePrefix(tableNamePrefixA);
            }
        }

        if (tableNamePrefixB != null) {
            for (TableInfo tableInfo : tableInfosB) {
                tableInfo.setNamePrefix(tableNamePrefixB);
            }
        }

        if (tableNamePrefixA != null || tableNamePrefixB != null) {
            String aAndB = (tableNamePrefixA == null) ? "" : tableNamePrefixA;
            aAndB = (tableNamePrefixB == null) ? aAndB : aAndB+tableNamePrefixB;
            for (TableInfo tableInfo : tableInfosAandB) {
                tableInfo.setNamePrefix(aAndB);
            }
        }

        allTables.addAll(tableInfosA);
        allTables.addAll(tableInfosB);
        allTables.addAll(tableInfosAandB);
        allTables.addAll(refTableInfos);
        allTables = Collections.unmodifiableList(allTables);
        return allTables;
    }

    @Override
    protected List<TableInfo> getRefTableInfos() {
        return refTableInfos;
    }

    @Override
    protected void addErrorLogTablePairs(DBConsumersManager manager) {
        Path errorLogA = PropsUtil.getPath(localAttrs.get("errorLogFileA"), null);
        if (errorLogA == null) {
            return;
        }
        manager.addErrorLogTablePair(errorLogA, ExtractComparer.EXTRACT_EXCEPTION_TABLE_A);
        Path errorLogB = PropsUtil.getPath(localAttrs.get("errorLogFileB"), null);
        if (errorLogB == null) {
            return;
        }
        manager.addErrorLogTablePair(errorLogB, ExtractComparer.EXTRACT_EXCEPTION_TABLE_B);

    }

}
