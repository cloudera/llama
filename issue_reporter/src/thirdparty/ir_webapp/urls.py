"""
This file contains URL specifications for /*
"""

from django.conf.urls.defaults import *

from django.conf import settings

from django.contrib import admin
admin.autodiscover()

urlpatterns = patterns('',
  (r'^admin/(.*)', admin.site.root),

  # point requests to / to the home index view, which
  # just redirects to /issues/
  (r'^$', 'ir_webapp.home.views.index'),

  # deal with static files using Django's built-in
  # static server.  Note that this is only for
  # development purposes.  This solution should not
  # be implemented in production
  (r'^static/(?P<path>.*)$', 'django.views.static.serve',
    {'document_root': settings.STATIC_DOC_ROOT}),

  # for all URLs that start with /issues/, have the
  # issues URL settings file do the work
  (r'^issues/', include('ir_webapp.issues.urls')),
)
