/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.plugin.etl.batch.sink;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.batch.OutputFormatProvider;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.plugin.PluginConfig;
import co.cask.cdap.etl.api.Emitter;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.cdap.etl.api.batch.BatchSink;
import co.cask.cdap.etl.api.batch.BatchSinkContext;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * HDFS Sink
 */
@Plugin(type = "batchsink")
@Name("HDFS")
@Description("Batch HDFS Sink")
public class HDFSSink extends BatchSink<StructuredRecord, Text, Text> {
  private HDFSSinkConfig config;

  public HDFSSink(HDFSSinkConfig config) {
    this.config = config;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    super.configurePipeline(pipelineConfigurer);
    // Verify if the timeSuffix format is valid.
    if (!Strings.isNullOrEmpty(config.timeSufix)) {
      new SimpleDateFormat(config.timeSufix);
    }
  }

  @Override
  public void prepareRun(BatchSinkContext context) throws Exception {
    context.addOutput(config.path, new SinkOutputFormatProvider(config, context));
  }

  @Override
  public void transform(StructuredRecord input, Emitter<KeyValue<Text, Text>> emitter) throws Exception {
    List<String> dataArray = new ArrayList<>();
    for (Schema.Field field : input.getSchema().getFields()) {
      dataArray.add(input.get(field.getName()).toString());
    }
    emitter.emit(new KeyValue<>(new Text(Joiner.on(",").join(dataArray)), new Text()));
  }

  public static class SinkOutputFormatProvider implements OutputFormatProvider {
    private final Map<String, String> conf;
    private final HDFSSinkConfig config;

    public SinkOutputFormatProvider(HDFSSinkConfig config, BatchSinkContext context) {
      this.conf = new HashMap<>();
      this.config = config;
      String timeSuffix = !Strings.isNullOrEmpty(config.timeSufix) ?
        new SimpleDateFormat(config.timeSufix).format(context.getLogicalStartTime()) : "";
      conf.put(FileOutputFormat.OUTDIR, String.format("%s/%s", config.path, timeSuffix));
      conf.put(JobContext.MAP_OUTPUT_KEY_CLASS, Text.class.getName());
      conf.put(JobContext.MAP_OUTPUT_VALUE_CLASS, Text.class.getName());
    }

    @Override
    public String getOutputFormatClassName() {
      return outputFormatClassName("TEXT");
    }

    @Override
    public Map<String, String> getOutputFormatConfiguration() {
      return conf;
    }
  }

  private static String outputFormatClassName(String option) {
    // Use option to extend it to more output formats
    return TextOutputFormat.class.getName();
  }

  /**
   * Config for HDFSSinkConfig.
   */
  public static class HDFSSinkConfig extends PluginConfig {

    @Name("path")
    @Description("HDFS Destination Path Prefix. By default it is empty")
    private String path;

    @Name("suffix")
    @Description("Time Suffix used for destination directory for each run. For example, 'YYYY-MM-DD-HH-MM'. " +
      "By default, no time suffix is used.")
    @Nullable
    private String timeSufix;

    public HDFSSinkConfig(String path, String suffix, String outputFormat) {
      this.path = path;
      this.timeSufix = suffix;
    }
  }
}
