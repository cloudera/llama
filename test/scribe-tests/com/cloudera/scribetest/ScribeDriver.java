// (c) Copyright 2008 Cloudera, Inc.

package com.cloudera.scribetest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;

/**
 * Driver program to test Scribe installation on the Cloudera Hadoop Distro
 *
 * Takes as argument a magic string to insert in the log4j logs. If Scribe
 * is running, these logs will be propagated back to the master. We then
 * check for them there.
 *
 * @author aaron
 *
 */
public final class ScribeDriver {

  /** private c'tor; this is a static-only class */
  private ScribeDriver() { }

  /** the param used to transmit the magic string to the mapper */
  public static final String MAGIC_STRING_KEY = "cloudera.magic.string";

  /** Paths where the job should run */
  public static final String INPUT_PATH = "scribe_test_input";
  public static final String OUTPUT_PATH = "scribe_test_output";

  // just something to give the LineRecordReader an actual record. text ignored.
  private static final String JUNK_TEXT_LINE = "mu";

  /** Create input file for MapReduce job */
  private static void makeInputs() throws IOException {
    FileSystem fs = FileSystem.get(new Configuration());

    if (!fs.exists(new Path(INPUT_PATH))) {
      fs.mkdirs(new Path(INPUT_PATH));
    }

    String filePath = INPUT_PATH;
    if (!filePath.endsWith("/")) {
      filePath = filePath + "/";
    }

    filePath = filePath + "input-file.txt";

    OutputStream ostream = fs.create(new Path(filePath));
    if (null == ostream) {
      throw new IOException("Could not create input file!");
    }


    OutputStreamWriter oswriter = null;
    BufferedWriter writer = null;
    try {
      oswriter = new OutputStreamWriter(ostream);
      writer = new BufferedWriter(oswriter);

      writer.append(JUNK_TEXT_LINE);
    } finally {
      // close layers in order.

      if (writer != null) {
        try {
          writer.close();
        } catch (IOException ioe) {
          // ignored; we're closing.
        }
      }

      if (oswriter != null) {
        try {
          oswriter.close();
        } catch (IOException ioe) {
          // ignored; we're closing.
        }
      }

      ostream.close();
    }
  }

  /**
   * Remove the output directory before running the mapreduce job.
   * @throws IOException
   */
  private static void removeOutputDir() throws IOException {
    FileSystem fs = FileSystem.get(new Configuration());
    if (fs.exists(new Path(OUTPUT_PATH))) {
      fs.delete(new Path(OUTPUT_PATH), true);
    }
  }

  /**
   * @param args
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Error; usage is: ScribeDriver <magic-string>");
      System.exit(1);
    }

    String magicString = args[0];

    makeInputs();
    removeOutputDir();

    JobConf conf = new JobConf(ScribeDriver.class);

    // set up the paths. The default TextInputFormat is expected.
    // Output format is irrelevant.
    FileInputFormat.addInputPath(conf, new Path(INPUT_PATH));
    FileOutputFormat.setOutputPath(conf, new Path(OUTPUT_PATH));

    // put user-specified magic string in the config
    conf.set(MAGIC_STRING_KEY, magicString);

    // set up the Mapper
    conf.setMapperClass(ScribeTestMapper.class);

    // no reducer necessary here.
    conf.setNumReduceTasks(0);

    JobClient.runJob(conf);
  }
}
