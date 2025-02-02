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

package com.google.zetasql.toolkit.usage;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQuery.JobListOption;
import com.google.cloud.bigquery.BigQueryOptions;
import java.util.Optional;

public class UsageTrackerImpl implements UsageTracker {
  private Optional<BigQuery> bigqueryClient;

  public UsageTrackerImpl() {
    try {
      this.bigqueryClient =
          Optional.of(
              BigQueryOptions.newBuilder()
                  .setHeaderProvider(UsageTracking.HEADER_PROVIDER)
                  .build()
                  .getService());
    } catch (Exception err) {
      this.bigqueryClient = Optional.empty();
    }
  }

  @Override
  public void trackUsage() {
    bigqueryClient.ifPresent(
        client -> {
          try {
            client.listJobs(JobListOption.pageSize(0));
          } catch (Exception ignored) {
          }
        });
  }
}
