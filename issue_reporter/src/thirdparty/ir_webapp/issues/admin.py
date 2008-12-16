"""
This file contains customizations for the admin utility
"""

from ir_webapp.issues.models import Issue
from django.contrib import admin

class IssueAdmin(admin.ModelAdmin):
  # break the admin form in to two sets: one for the simple data
  # and the other for the description
  fieldsets = [
               (None, {'fields': ['name', 'reporter_email', 'submit_date']}),
               ("Details", {'fields': ['description']}),
                     ]

  # display these variables when viewing a list of issues
  list_display = ('name', 'reporter_email', 'submit_date')

  # allow filtering on these variables
  list_filter = ['reporter_email', 'submit_date']

  # allow searching on these fields
  search_fields = ['name', 'reporter_email', 'description']

  # allow for a date hierarchy as well
  date_hierarchy = 'submit_date'

admin.site.register(Issue, IssueAdmin)