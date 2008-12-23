// (c) Copyright 2008 Cloudera, Inc.

package com.cloudera.scribetest;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.OutputCollector;

public class ScribeTestMapper extends MapReduceBase
  implements Mapper<LongWritable, Text, LongWritable, Text> {

  public static final Log LOG =
    LogFactory.getLog("com.cloudera.scribetest.ScribeTestMapper");

  private static final String MAGIC_PREFIX = "Cloudera-Magic-Prefix-";
  private static final String DEFAULT_MAGIC_STRING = "undefined-extra-magic";
  private String magicString;

  public ScribeTestMapper() {
    this.magicString = null;
  }

  /**
   * Grab the magic string the user configured in the driver.
   */
  public void configure(JobConf conf) {
    this.magicString = conf.get(ScribeDriver.MAGIC_STRING_KEY);
    if (null == this.magicString) {
      this.magicString = DEFAULT_MAGIC_STRING;
    }
  }

  public void map(LongWritable key, Text val, OutputCollector<LongWritable, Text> output,
      Reporter reporter) throws IOException {
    // emit the log entry, once per record.
    // (We should really only get one record in here anyway)
    LOG.info(MAGIC_PREFIX + this.magicString);
  }
}
