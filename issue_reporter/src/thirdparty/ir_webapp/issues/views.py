"""
Views for the issues web app
"""

from ir_webapp.issues.models import Issue
from ir_webapp.issues.models import IssueForm

from django.shortcuts import render_to_response, get_object_or_404
from django.http import HttpResponseRedirect
from django.core.urlresolvers import reverse

import datetime

def new(request):
  """
  The new view displays an empty form, or
  displays a filled out form with errors, or
  creates a new issue if the form was
  filled out correctly
  """
  # if the user has submitted the form
  if request.method == 'POST':
    form = IssueForm(request.POST)
    # validate the form
    if form.is_valid():

      # fetch all of the form information
      name = form.cleaned_data['name']
      reporter_email = form.cleaned_data['reporter_email']
      description = form.cleaned_data['description']
      submit_date = datetime.datetime.now()

      # create and save the issue
      i = Issue(
        name=name,
        reporter_email=reporter_email,
        description=description,
        submit_date=submit_date,
      )
      i.save()

      # TODO: send this issue to Cloudera

      # redirect to the issue results page
      return HttpResponseRedirect(reverse('issue_results', args=(i.id,)))
  # if the user hasn't submitted a form yet
  else:
    form = IssueForm()

  return render_to_response('issues/new.html', {
    'form': form,
  })
