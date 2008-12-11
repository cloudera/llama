"""
This file contains URL specifications for /issues/*
"""

from django.conf.urls.defaults import *
from ir_webapp.issues.models import Issue

# the info dict is used for the Django generic views
info_dict = {
    'queryset': Issue.objects.all(),
}

urlpatterns = patterns('',
  # Django generic views are used here.  To learn about the semantics of
  # these generic views, visit
  # http://docs.djangoproject.com/en/dev/ref/generic-views/

  # the issue list, name it for a reverse URL lookup
  #
  # according to Django's generic views, the issues/issues_list.html template
  # will be used
  (r'^$', 'django.views.generic.list_detail.object_list', info_dict, 'issue_list'),

  # a single issue, name it for reverse URL lookup
  #
  # according to Django's generic views, the issues/issues_detail.html template
  # will be used
  (r'^(?P<object_id>\d+)/$', 'django.views.generic.list_detail.object_detail', info_dict, 'issue_detail'),

  # this is the same as an issue detail, except that this URL is used when a
  # new issue is created.  The only difference is a "Your issue has been created" message
  #
  # the info dictionary passed along specifies which template
  # to use
  (r'^(?P<object_id>\d+)/results/$', 'django.views.generic.list_detail.object_detail', dict(info_dict, template_name='issues/results.html'), 'issue_results'),

  # create a new issue.  this view specifies
  # which template it will use
  (r'^new/$', 'ir_webapp.issues.views.new'),
)
