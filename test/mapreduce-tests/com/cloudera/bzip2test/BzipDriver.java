// (c) Copyright 2009 Cloudera, Inc.

package com.cloudera.bzip2test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.lib.IdentityMapper;
import org.apache.hadoop.mapred.lib.IdentityReducer;

/**
 * Driver program to test Bzip2 integration the Cloudera Hadoop Distro
 *
 * Writes out a text file that contains the integers 0..100.
 * Uses a MR job to write this to a single sequence file, using bz2
 * compression.
 * Uses a second MR job to write this all back to a single text file.
 * Verifies that the output file contains the same contents as the
 * original.
 *
 * @author aaron
 *
 */
public final class BzipDriver {

  /** private c'tor; this is a static-only class */
  private BzipDriver() { }

  /** Paths where the job runs */
  public static final String INPUT_PATH = "bzip2_test_input";
  public static final String INTERMEDIATE_PATH = "bzip2_test_intermediate";
  public static final String OUTPUT_PATH = "bzip2_test_output";
  public static final String OUTPUT_FILENAME = OUTPUT_PATH + "/part-00000";

  // we write lines from  [0 .. MAX_RECORD_NUM) into a file
  private static final int MAX_RECORD_NUM = 100;

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

      for (int i = 0; i < MAX_RECORD_NUM; i++) {
        writer.append(Integer.toString(i) + "\n");
      }
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

      try {
        ostream.close();
      } catch (IOException ioe) {
        // ignored; we're closing.
      }
    }
  }

  /**
   * Remove the intermediate directory before running the mapreduce job.
   * @throws IOException
   */
  private static void removeIntermediateDir() throws IOException {
    FileSystem fs = FileSystem.get(new Configuration());
    if (fs.exists(new Path(INTERMEDIATE_PATH))) {
      fs.delete(new Path(INTERMEDIATE_PATH), true);
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
   * Input text file gets converted to a sorted sequence file
   */
  private static void textToSequence() throws IOException {
    JobConf conf = new JobConf(BzipDriver.class);

    // set up the paths. The default TextInputFormat is expected.
    // Output format is bz2-compressed sequence file.
    FileInputFormat.addInputPath(conf, new Path(INPUT_PATH));
    FileOutputFormat.setOutputPath(conf, new Path(INTERMEDIATE_PATH));

    // set up the Mapper -- just dump all the lines into a sequence file
    // with an identity mapper.
    conf.setMapperClass(IdentityMapper.class);

    // set output format and compression.
    conf.setOutputFormat(SequenceFileOutputFormat.class);
    SequenceFileOutputFormat.setCompressOutput(conf, true);
    SequenceFileOutputFormat.setOutputCompressionType(conf, CompressionType.BLOCK);
    SequenceFileOutputFormat.setOutputCompressorClass(conf, BZip2Codec.class);

    // no reducer necessary here.
    conf.setNumReduceTasks(0);

    JobClient.runJob(conf);
  }

  /**
   * Read the output part-00000 file in the output directory and verify
   * that it contains all the numbers that we'd like to see.
   */
  private static void validateResults() throws IOException {
    FileSystem fs = FileSystem.get(new Configuration());
    Path p = new Path(OUTPUT_FILENAME);
    InputStream is = fs.open(p);
    if (null == is) {
      throw new IOException("Couldn't open output file for reading!");
    }

    InputStreamReader isreader = null;
    BufferedReader reader = null;
    try {
      isreader = new InputStreamReader(is);
      reader = new BufferedReader(isreader);

      int expected = 0;

      while (true) {
        String line = reader.readLine();
        if (null == line) {
          // we've reached EOF.
          break;
        }

        // we expect these lines to contain individual integers, in sorted order
        int actual = Integer.valueOf(line.trim());
        if (actual != expected) {
          throw new RuntimeException("Found line with value " + actual + "; expected " + expected);
        }

        if (actual >= MAX_RECORD_NUM) {
          // we didn't write this many records, so something's amiss.
          throw new RuntimeException("Got extra record: " + actual);
        }

        // the next line expects the next-highest integer.
        expected++;
      }
    } finally {
      // close layers in order.
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ioe) {
          // ignored; we're closing.
        }
      }

      if (isreader != null) {
        try {
          isreader.close();
        } catch (IOException ioe) {
          // ignored; we're closing.
        }
      }

      try {
        is.close();
      } catch (IOException ioe) {
        // ignored; we're closing.
      }
    }
  }

  private static void sequenceToText() throws IOException {
    JobConf conf = new JobConf(BzipDriver.class);

    // setup the paths. Input is sequence format, output is text.
    FileInputFormat.addInputPath(conf, new Path(INTERMEDIATE_PATH));
    FileOutputFormat.setOutputPath(conf, new Path(OUTPUT_PATH));

    conf.setMapperClass(ValuesToKeysMapper.class);
    conf.setReducerClass(IdentityReducer.class);

    conf.setInputFormat(SequenceFileInputFormat.class);
    conf.setOutputValueClass(NullWritable.class);
    conf.setNumReduceTasks(1);

    JobClient.runJob(conf);
  }

  /**
   * @param args
   */
  public static void main(String[] args) throws IOException {
    makeInputs();
    removeIntermediateDir();
    removeOutputDir();
    textToSequence();
    sequenceToText();
    validateResults();
  }
}
