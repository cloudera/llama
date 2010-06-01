# Copyright (c) 2010 Cloudera, inc.


class Constants:

  # Cloudera network
  CLOUDERA_NETWORK = '38.102.147.0/24'



def verbose_print(msg='', verbose=True):
  '''
  Only print if we are not in quiet mode is set

  @param msg Message to display
  @param verbose If not verbose, no message should be displayed
  '''

  if verbose:
    print(msg)



def display_message(msg, verbose=True):
  '''
  Nicely format a message in order to make it distinct from random logging

  @param msg Message to display
  @param verbose If not verbose, no message should be displayed
  '''

  verbose_print()
  verbose_print(msg, verbose)
  verbose_print("=" * len(msg), verbose)

