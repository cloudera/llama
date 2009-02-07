// (c) Copyright 2009 Cloudera, Inc.

package com.cloudera.bzip2test;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.OutputCollector;

/**
 * Map (k, v) to (long(v), null)
 */
public class ValuesToKeysMapper extends MapReduceBase
  implements Mapper<LongWritable, Text, LongWritable, NullWritable> {

  public ValuesToKeysMapper() {
  }

  public void map(LongWritable key, Text val, OutputCollector<LongWritable, NullWritable> output,
      Reporter reporter) throws IOException {
    output.collect(new LongWritable(Long.valueOf(val.toString())), NullWritable.get());
  }
}
