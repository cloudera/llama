from django.http import HttpResponsePermanentRedirect

def index(request):
  """
  Redirect requests to the issues web app
  """
  return HttpResponsePermanentRedirect('/issues/')
