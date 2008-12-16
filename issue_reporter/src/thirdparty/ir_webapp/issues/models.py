"""
Describes the models for the issue web app
"""

from django.db import models
from django.forms import ModelForm

class Issue(models.Model):
  """
  An issue class.  Pretty basic
  """
  name = models.CharField(max_length=255)
  reporter_email = models.EmailField()
  description = models.TextField()
  submit_date = models.DateTimeField('date submitted')

  def __unicode__(self):
    return self.name

  class Meta:
    ordering = ('-submit_date', 'name')

class IssueForm(ModelForm):
  """
  A Form object that matches
  the Issue class
  """
  class Meta:
    model = Issue
    # we don't want the user to have to specify
    # the submit_date, instead we want the
    # submit date to be now()
    exclude = ('submit_date',)