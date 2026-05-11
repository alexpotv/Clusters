# Clusters

Clusters is an Android app, which is meant to be installed on the Android-based head unit of 10th generation Honda Civic models (2016-2021). The app runs on the Android 4.2.2 head unit, and overrides display on the external display, which is the instrument cluster's info screen (in some contexts).

When started, the app send to the cluster the signal that it's now rendering a display (overriding the cluster's info display). From there, the app uses the main display so the user can control what's shown in the cluster screen. When the app exits, the rendering is stopped.

The cluster screen is only rendered in part of the external screen, because only this part is visible in the cluster. Some of these screens use vehicle data or other data, based on usage from reverse-engineered existing apps.
