/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zetasql.toolkit.antipattern.cmd;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BQAntiPatternCMDParser {

  private static final Logger logger = LoggerFactory.getLogger(BQAntiPatternCMDParser.class);

  public static final String QUERY_OPTION_NAME = "query";
  public static final String FILE_PATH_OPTION_NAME = "input_file_path";
  public static final String FOLDER_PATH_OPTION_NAME = "input_folder_path";
  public static final String INPUT_CSV_FILE_OPTION_NAME = "input_csv_file_path";
  public static final String OUTPUT_FILE_OPTION_NAME = "output_file_path";
  public static final String READ_FROM_INFO_SCHEMA_FLAG_NAME = "read_from_info_schema";
  public static final String READ_FROM_INFO_SCHEMA_DAYS_OPTION_NAME = "read_from_info_schema_days";
  public static final String READ_FROM_INFO_SCHEMA_TABLE_OPTION_NAME = "info_schema_table_name";
  public static final String PROCESSING_PROJECT_ID_OPTION_NAME = "processing_project_id";
  public static final String OUTPUT_TABLE_OPTION_NAME = "output_table";

  private Options options;
  private CommandLine cmd;

  public BQAntiPatternCMDParser(String[] args) throws ParseException {
    options = getOptions();
    CommandLineParser parser = new BasicParser();
    cmd = parser.parse(options, args);
  }

  public String getOutputTable() {
    return cmd.getOptionValue(OUTPUT_TABLE_OPTION_NAME);
  }

  public String getProcessingProject() {
    return cmd.getOptionValue(PROCESSING_PROJECT_ID_OPTION_NAME);
  }

  public String getOutputFileOptionName() {
    return cmd.getOptionValue(OUTPUT_FILE_OPTION_NAME);
  }

  public boolean hasOutputFileOptionName() {
    return cmd.hasOption(OUTPUT_FILE_OPTION_NAME);
  }

  public boolean isReadingFromInfoSchema() {
    return cmd.hasOption(READ_FROM_INFO_SCHEMA_FLAG_NAME);
  }

  public boolean hasOutputTable() {
    return cmd.hasOption(OUTPUT_TABLE_OPTION_NAME);
  }

  public Options getOptions() {
    Options options = new Options();
    Option query =
        Option.builder(QUERY_OPTION_NAME)
            .argName(QUERY_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("set query")
            .build();
    options.addOption(query);

    Option filePath =
        Option.builder(FILE_PATH_OPTION_NAME)
            .argName(FILE_PATH_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("set file path")
            .build();
    options.addOption(filePath);

    Option folderPath =
        Option.builder(FOLDER_PATH_OPTION_NAME)
            .argName(FOLDER_PATH_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("set file path")
            .build();
    options.addOption(folderPath);

    Option useInfoSchemaFlag =
        Option.builder(READ_FROM_INFO_SCHEMA_FLAG_NAME)
            .argName(READ_FROM_INFO_SCHEMA_FLAG_NAME)
            .required(false)
            .desc("flag specifying if the queries should be read from INFORMATION_SCHEMA")
            .build();
    options.addOption(useInfoSchemaFlag);

    Option procesingProjectOption =
        Option.builder(PROCESSING_PROJECT_ID_OPTION_NAME)
            .argName(PROCESSING_PROJECT_ID_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("project where the solution will execute")
            .build();
    options.addOption(procesingProjectOption);

    Option outputTableOption =
        Option.builder(OUTPUT_TABLE_OPTION_NAME)
            .argName(OUTPUT_TABLE_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("project with the table to which output will be written")
            .build();
    options.addOption(outputTableOption);

    Option outputFileOption =
        Option.builder(OUTPUT_FILE_OPTION_NAME)
            .argName(OUTPUT_FILE_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("path to csv file for result output")
            .build();
    options.addOption(outputFileOption);

    Option inputCsvFileOption =
        Option.builder(INPUT_CSV_FILE_OPTION_NAME)
            .argName(INPUT_CSV_FILE_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("path to csv file with input queries")
            .build();
    options.addOption(inputCsvFileOption);

    Option infoSchemaDays =
        Option.builder(READ_FROM_INFO_SCHEMA_DAYS_OPTION_NAME)
            .argName(READ_FROM_INFO_SCHEMA_DAYS_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("Specifies how many days back should INFORMATION SCHEMA be queried for")
            .build();
    options.addOption(infoSchemaDays);

    Option infoSchemaTable =
        Option.builder(READ_FROM_INFO_SCHEMA_TABLE_OPTION_NAME)
            .argName(READ_FROM_INFO_SCHEMA_TABLE_OPTION_NAME)
            .hasArg()
            .required(false)
            .desc("Specifies how many days back should INFORMATION SCHEMA be queried for")
            .build();
    options.addOption(infoSchemaTable);

    return options;
  }

  public Iterator<InputQuery> getInputQueries() {
    try {
      if (cmd.hasOption(READ_FROM_INFO_SCHEMA_FLAG_NAME)) {
        return readFromIS();
      } else if (cmd.hasOption(QUERY_OPTION_NAME)) {
        return buildIteratorFromQueryStr(cmd.getOptionValue(QUERY_OPTION_NAME));
      } else if (cmd.hasOption(FILE_PATH_OPTION_NAME)) {
        return buildIteratorFromFilePath(cmd.getOptionValue(FILE_PATH_OPTION_NAME));
      } else if (cmd.hasOption(FOLDER_PATH_OPTION_NAME)) {
        return buildIteratorFromFolderPath(cmd.getOptionValue(FOLDER_PATH_OPTION_NAME));
      } else if (cmd.hasOption(INPUT_CSV_FILE_OPTION_NAME)) {
        return buildIteratorFromCSV(cmd.getOptionValue(INPUT_CSV_FILE_OPTION_NAME));
      }
    } catch (IOException | InterruptedException e) {
      System.out.println(e.getMessage());
      System.exit(0);
    }
    return null;
  }

  private Iterator<InputQuery> readFromIS() throws InterruptedException {
    logger.info("Using INFORMATION_SCHEMA as input source");
    if (cmd.hasOption(READ_FROM_INFO_SCHEMA_DAYS_OPTION_NAME)
        && cmd.hasOption(READ_FROM_INFO_SCHEMA_TABLE_OPTION_NAME)) {
      return new InformationSchemaQueryIterable(
          cmd.getOptionValue(PROCESSING_PROJECT_ID_OPTION_NAME),
          cmd.getOptionValue(READ_FROM_INFO_SCHEMA_DAYS_OPTION_NAME),
          cmd.getOptionValue(READ_FROM_INFO_SCHEMA_TABLE_OPTION_NAME));
    } else if (cmd.hasOption(READ_FROM_INFO_SCHEMA_FLAG_NAME)) {
      return new InformationSchemaQueryIterable(
          cmd.getOptionValue(PROCESSING_PROJECT_ID_OPTION_NAME),
          cmd.getOptionValue(READ_FROM_INFO_SCHEMA_DAYS_OPTION_NAME));
    } else {
      return new InformationSchemaQueryIterable(
          cmd.getOptionValue(PROCESSING_PROJECT_ID_OPTION_NAME));
    }
  }

  public static Iterator<InputQuery> buildIteratorFromQueryStr(String queryStr) {
    logger.info("Using inline query as input source");
    InputQuery inputQuery = new InputQuery(queryStr, "inline query");
    return (new ArrayList<>(Arrays.asList(inputQuery))).iterator();
  }

  public static Iterator<InputQuery> buildIteratorFromFilePath(String filePath) {
    logger.info("Using sql file as input source");
    // Using the folder query iterator with a single file
    return new InputFolderQueryIterable(new ArrayList<>(Arrays.asList(filePath)));
  }

  private static Iterator<InputQuery> buildIteratorFromCSV(String inputCSVPath) throws IOException {
    logger.info("Using csv file as input source");
    return new InputCsvQueryIterator(inputCSVPath);
  }

  private static Iterator<InputQuery> buildIteratorFromFolderPath(String folderPath) {
    logger.info("Using folder as input source");
    if (folderPath.startsWith("gs://")) {
      logger.info("Reading input folder from GCS");
      Storage storage = StorageOptions.newBuilder().build().getService();
      String trimFolderPathStr = folderPath.replace("gs://", "");
      List<String> list = new ArrayList(Arrays.asList(trimFolderPathStr.split("/")));
      String bucket = list.get(0);
      list.remove(0);
      String directoryPrefix = String.join("/", list) + "/";
      Page<Blob> blobs =
          storage.list(
              bucket,
              Storage.BlobListOption.prefix(directoryPrefix),
              Storage.BlobListOption.currentDirectory());
      ArrayList gcsFileList = new ArrayList();
      for (Blob blob : blobs.iterateAll()) {
        String blobName = blob.getName();
        if (blobName.equals(directoryPrefix)) {
          continue;
        }
        gcsFileList.add("gs://" + bucket + "/" + blobName);
      }
      return new InputFolderQueryIterable(gcsFileList);
    } else {
      logger.info("Reading input folder from local");
      List<String> fileList =
          Stream.of(new File(folderPath).listFiles())
              .filter(file -> file.isFile())
              .map(File::getAbsolutePath)
              .collect(Collectors.toList());
      return new InputFolderQueryIterable(fileList);
    }
  }
}
