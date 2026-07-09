# Clusters

> **AI Usage Disclaimer**
> 
> Since I'm not an Android developer, I've used AI-based tools to produce the code in this repository. By using this repo, you agree to review the code you're using, to apply your judgment, and to use critical thinking.

> **Responsibility Disclaimer**
> 
> This repository is a hobby project, and is provided with no guarantee. By using this repo, you agree to the risks of running custom software on proprietary hardware (such as a vehicle's head unit).

**Shoutout to [the ic1101 repo](https://github.com/librick/ic1101) for the inspiration!**

Clusters is an Android app, which is meant to be installed on the Android-based head unit of 10th generation Honda Civic models (2016-2021). The app runs on the Android 4.2.2 head unit, and overrides display on the external display, which is the instrument cluster's info screen (in some contexts).

When started, the app sends to the cluster the signal that it's now rendering a display (overriding the cluster's info display). The app runs in the background, keeps settings related to the current vehicle, and exposes the vehicle/system data through the `cluster-plugin-api` module so other apps can implement their own cluster content. The main display's built-in screens exist to test and debug the app and to drive initial setup.

## Current State of the Repo

Right now, there are still many artifacts of the repo's creation. The app has debug screens all over, there is not enough interfaces for features specific to some car models, the main app's structure is too heavy, and many of the exposed vehicle attributes don't yet work. This will change as I get more time to clean up and work on the repository, but in the meantime, it's being shared this way for others to explore too. Please see `CONTRIBUTING.md`.

**Please note that the exposed APIs are not stable yet, and are subject to change. This is one of the main reasons why contributions are restricted for now.**